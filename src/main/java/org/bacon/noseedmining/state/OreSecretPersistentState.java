package org.bacon.noseedmining.state;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads and caches the world-specific secret that is mixed into ore generation RNG.
 */
public final class OreSecretPersistentState {
    private static final Logger LOGGER = LoggerFactory.getLogger("noseedmining");
    private static final String SECRET_KEY = "secret";
    private static final String SECRET_FILE_NAME = "noseedmining_secret.dat";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ThreadLocal<MessageDigest> DIGEST = ThreadLocal.withInitial(OreSecretPersistentState::createDigest);
    private static final Map<Path, OreSecretPersistentState> CACHE = new ConcurrentHashMap<>();

    private final long secret;

    private OreSecretPersistentState(long secret) {
        this.secret = secret;
    }

    public long secret() {
        return this.secret;
    }

    /**
     * Ensures the world secret is loaded for the provided server world.
     */
    public static OreSecretPersistentState get(ServerWorld world) {
        Path secretFile = getSecretFile(world);
        return CACHE.computeIfAbsent(secretFile, path -> load(world, path));
    }

    /**
     * Mixes the stored secret into the provided random instance used during ore generation.
     */
    public static void mixRandom(StructureWorldAccess world, Random random, BlockPos origin) {
        ServerWorld serverWorld;
        try {
            serverWorld = world.toServerWorld();
        } catch (UnsupportedOperationException e) {
            LOGGER.debug("Skipping ore RNG mixing for non-server world implementation: {}", world.getClass().getName());
            return;
        }

        OreSecretPersistentState state = get(serverWorld);
        long worldSeed = world.getSeed();
        long chunkSeed = ChunkPos.toLong(origin.getX() >> 4, origin.getZ() >> 4);
        int sectionY = origin.getY() >> 4;
        long mixedSeed = mix(state.secret, worldSeed, chunkSeed, sectionY);
        random.setSeed(mixedSeed);
    }

    private static OreSecretPersistentState load(ServerWorld world, Path file) {
        long value = readSecret(file);
        if (value == Long.MIN_VALUE) {
            value = generateSecret();
            writeSecret(file, value);
            LOGGER.info("Generated new ore secret for world '{}'.", world.getRegistryKey().getValue());
        }
        return new OreSecretPersistentState(value);
    }

    private static long readSecret(Path file) {
        if (!Files.exists(file)) {
            return Long.MIN_VALUE;
        }

        try {
            NbtCompound nbt = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
            if (nbt != null && nbt.contains(SECRET_KEY)) {
                Optional<Long> maybeSecret = nbt.getLong(SECRET_KEY);
                if (maybeSecret.isPresent()) {
                    return maybeSecret.get();
                }
            }
            LOGGER.warn("Secret file '{}' was missing expected data; regenerating secret.", file);
        } catch (IOException exception) {
            LOGGER.warn("Failed to read ore secret from '{}'; regenerating secret.", file, exception);
        }
        return Long.MIN_VALUE;
    }

    private static void writeSecret(Path file, long secret) {
        try {
            Files.createDirectories(file.getParent());
            NbtCompound nbt = new NbtCompound();
            nbt.putLong(SECRET_KEY, secret);
            NbtIo.writeCompressed(nbt, file);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist NoSeedMining secret", exception);
        }
    }

    private static long generateSecret() {
        long value;
        do {
            value = SECURE_RANDOM.nextLong();
        } while (value == 0L);
        return value;
    }

    private static Path getSecretFile(ServerWorld world) {
        Path baseDirectory = world.getServer().getSavePath(WorldSavePath.ROOT);
        Path dataDirectory = baseDirectory.resolve("data");
        return dataDirectory.resolve(SECRET_FILE_NAME);
    }

    private static long mix(long secret, long worldSeed, long chunkSeed, int sectionY) {
        MessageDigest digest = DIGEST.get();
        digest.reset();
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES * 3 + Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(secret);
        buffer.putLong(worldSeed);
        buffer.putLong(chunkSeed);
        buffer.putInt(sectionY);
        byte[] hash = digest.digest(buffer.array());
        return ByteBuffer.wrap(hash).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    private static MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Missing SHA-256 algorithm", exception);
        }
    }
}
