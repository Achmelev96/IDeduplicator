package Model;

import java.math.BigInteger;
import java.nio.file.Path;
import java.sql.*;
import java.util.Optional;

public final class HashCache implements AutoCloseable {

    public record CachedHash(int algoId, int bitLength, byte[] hashBytes, long lastModified, long fileSize) {}

    private final Connection conn;

    public HashCache(String dbFilePath) {
        try {
            conn = DriverManager.getConnection("jdbc:h2:file:" + dbFilePath + ";AUTO_SERVER=TRUE");
            init();
        } catch (SQLException e) {
            throw new RuntimeException("Cannot open H2", e);
        }
    }

    private void init() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS image_hash (
                  path VARCHAR PRIMARY KEY,
                  algo_id INT NOT NULL,
                  bit_len INT NOT NULL,
                  hash_bytes VARBINARY NOT NULL,
                  last_modified BIGINT NOT NULL,
                  file_size BIGINT NOT NULL
                )
            """);
        }
    }

    public Optional<CachedHash> get(Path path) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT algo_id, bit_len, hash_bytes, last_modified, file_size FROM image_hash WHERE path=?")) {
            ps.setString(1, path.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new CachedHash(
                        rs.getInt(1),
                        rs.getInt(2),
                        rs.getBytes(3),
                        rs.getLong(4),
                        rs.getLong(5)
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void put(Path path, int algoId, int bitLen, BigInteger hashValue, long lastModified, long fileSize) {
        byte[] bytes = toFixedUnsignedBytes(hashValue, bitLen);
        try (PreparedStatement ps = conn.prepareStatement("""
            MERGE INTO image_hash (path, algo_id, bit_len, hash_bytes, last_modified, file_size)
            KEY(path) VALUES (?, ?, ?, ?, ?, ?)
        """)) {
            ps.setString(1, path.toString());
            ps.setInt(2, algoId);
            ps.setInt(3, bitLen);
            ps.setBytes(4, bytes);
            ps.setLong(5, lastModified);
            ps.setLong(6, fileSize);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void remove(Path path) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM image_hash WHERE path=?")) {
            ps.setString(1, path.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] toFixedUnsignedBytes(BigInteger value, int bitLen) {
        int byteLen = (bitLen + 7) / 8;
        byte[] raw = value.toByteArray();
        byte[] out = new byte[byteLen];

        int srcPos = Math.max(0, raw.length - byteLen);
        int copyLen = Math.min(raw.length, byteLen);
        System.arraycopy(raw, srcPos, out, byteLen - copyLen, copyLen);

        return out;
    }

    @Override
    public void close() {
        try { conn.close(); } catch (SQLException ignored) {}
    }
}
