package Model;

import dev.brachtendorf.jimagehash.hash.Hash;
import dev.brachtendorf.jimagehash.hashAlgorithms.HashingAlgorithm;
import dev.brachtendorf.jimagehash.hashAlgorithms.PerceptiveHash;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public final class DuplicateScanner {

    public record Result(Set<Path> toSelect, int groupsFound) {}

    private final ExecutorService pool;
    private final HashCache cache;
    private final HashingAlgorithm hasher;
    private final int expectedBitLen;

    public DuplicateScanner(HashCache cache) {
        this.cache = cache;
        this.hasher = new PerceptiveHash(32);
        this.pool = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
        this.expectedBitLen = 32 * 32;
    }

    public CompletableFuture<Result> scanAsync(List<Path> images, double threshold) {
        return CompletableFuture.supplyAsync(() -> scan(images, threshold), pool);
    }

    private Result scan(List<Path> images, double threshold) {
        List<HashEntry> entries = new ArrayList<>(images.size());

        for (Path p : images) {
            try {
                if (!Files.isRegularFile(p)) continue;
                long lm = Files.getLastModifiedTime(p).toMillis();
                long sz = Files.size(p);

                HashEntry e = loadOrCompute(p, lm, sz);
                if (e != null) entries.add(e);

            } catch (Exception ignored) {}
        }

        List<Group> groups = new ArrayList<>();
        for (HashEntry e : entries) {
            boolean placed = false;

            for (Group g : groups) {
                double d = normalizedHammingDistance(e.hashValue, g.rep.hashValue, e.bitLen);
                if (d < threshold) {
                    g.items.add(e);
                    placed = true;
                    break;
                }
            }

            if (!placed) groups.add(new Group(e));
        }

        Set<Path> toSelect = new HashSet<>();
        int groupsFound = 0;

        for (Group g : groups) {
            if (g.items.size() <= 1) continue;
            groupsFound++;

            HashEntry keep = g.items.stream()
                    .max(Comparator.comparingLong(x -> x.size))
                    .orElse(g.items.get(0));

            for (HashEntry it : g.items) {
                if (!it.path.equals(keep.path)) toSelect.add(it.path);
            }
        }

        return new Result(toSelect, groupsFound);
    }

    private record HashEntry(Path path, BigInteger hashValue, int bitLen, long size) {}

    private static final class Group {
        final HashEntry rep;
        final List<HashEntry> items = new ArrayList<>();
        Group(HashEntry rep) { this.rep = rep; this.items.add(rep); }
    }

    private HashEntry loadOrCompute(Path p, long lastModified, long size) throws Exception {
        var cached = cache.get(p);
        if (cached.isPresent()) {
            var c = cached.get();

            if (c.lastModified() == lastModified && c.fileSize() == size) {
                int bitLen = c.bitLength();
                BigInteger hv = new BigInteger(1, c.hashBytes()); // unsigned
                return new HashEntry(p, hv, bitLen, size);
            }
        }

        Hash h = hasher.hash(new File(p.toString()));
        BigInteger hv = h.getHashValue();
        int bitLen = h.getBitResolution();
        int algoId = h.getAlgorithmId();

        cache.put(p, algoId, bitLen, hv, lastModified, size);

        return new HashEntry(p, hv, bitLen, size);
    }

    private static double normalizedHammingDistance(BigInteger a, BigInteger b, int bitLen) {
        BigInteger x = a.xor(b);
        int diff = x.bitCount();
        return (double) diff / (double) bitLen;
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}
