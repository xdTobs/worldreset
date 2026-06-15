package org.tobs.worldreset;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

public class WorldResetCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger("worldreset");

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("resetworld")
                    .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .executes(ctx -> execute(ctx.getSource()))
        );
    }

    private static int execute(ServerCommandSource source) {
        MinecraftServer server = source.getServer();

        broadcast(server, "§cWorld reset in 3 seconds. You will be disconnected.");

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        broadcast(server, "§cDisconnecting all players...");

        for (ServerPlayerEntity player : List.copyOf(server.getPlayerManager().getPlayerList())) {
            player.networkHandler.disconnect(Text.literal("§cServer is resetting. Rejoin shortly."));
        }

        server.execute(() -> {
            server.getOverworld().save(null, true, false);

            Path worldSavePath = server.getSavePath(WorldSavePath.ROOT);
            Path presetPath = server.getRunDirectory().resolve("config/world-reset/preset");

            if (!Files.exists(presetPath)) {
                LOGGER.error("Preset directory not found at {}. Aborting reset.", presetPath);
                return;
            }

            try {
                deleteRecursively(worldSavePath);
                copyPreset(presetPath, worldSavePath);
            } catch (IOException e) {
                LOGGER.error("World reset failed during file operations", e);
                return;
            }

            LOGGER.info("Reset complete. Stopping server for restart.");
            server.execute(() -> server.stop(false));
        });

        return 1;
    }

    private static void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void copyPreset(Path src, Path dst) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(dst.resolve(src.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, dst.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void broadcast(MinecraftServer server, String message) {
        server.getPlayerManager().broadcast(Text.literal(message), false);
        LOGGER.info(message);
    }
}
