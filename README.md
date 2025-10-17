# NoSeedMining

A Fabric mod for Minecraft 1.21.8 that prevents seed-based ore prediction tools from working.

## What It Does

This mod protects servers from players using seed-finding tools to locate valuable ores. It works by mixing a server-side secret into the random number generator used for ore placement, making seed-based predictions completely unreliable without affecting normal gameplay.

## How It Works

1. **Server Secret**: When a world is first created, the mod generates a cryptographically secure random secret that's stored in the world data
2. **RNG Mixing**: During ore generation, this secret is mixed with the world seed and chunk coordinates before being passed to the ore placement algorithm
3. **Transparent to Players**: The ore distribution looks and feels exactly the same as vanilla Minecraft, but can't be predicted even if players know the world seed

## Technical Details

The mod uses Mixin to inject into Minecraft's `OreFeature` class, replacing the random number generator used for ore placement with one seeded by:
- The server's secret (unknown to players)
- The world seed
- The chunk coordinates

This means:
- Ore distributions are deterministic per-world (same ore veins will generate each time)
- Players cannot predict ore locations even with the world seed
- No performance impact
- Fully compatible with vanilla clients

## Installation

### Server

1. Download the mod JAR file
2. Place it in your server's `mods` folder
3. Ensure you have Fabric Loader installed (version 0.17.3 or higher)
4. Restart the server

### Requirements

- Minecraft 1.21.8
- Fabric Loader 0.17.3+
- Fabric API 0.134.0+1.21.8

## Building from Source

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`

## Configuration

No configuration needed! The mod works automatically once installed.

## Compatibility

- **Client-side**: Not required on client (server-side only mod)
- **Other Mods**: Should be compatible with most mods that don't significantly alter ore generation

## License

Licensed under the Apache License 2.0. See LICENSE.txt for details.

## How to Verify It's Working

1. Install the mod on your server
2. Generate a new world or load an existing one
3. Check the server logs for `[noseedmining]` initialization messages
4. The mod will automatically create a secret stored in `world/data/noseedmining_secret.dat`

## For Server Administrators

- The secret is world-specific and persists across server restarts
- Deleting the `noseedmining_secret.dat` file will generate a new secret (and change all future ore generation)
- Existing chunks are not affected - only newly generated chunks use the secret

## Security Notes

- The server secret is generated using Java's `SecureRandom` class
- The secret is stored in NBT format in the world's persistent state
- Players cannot extract the secret through normal gameplay
- Even with access to the world seed, players cannot predict ore locations without the secret

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues.

## Credits

Created by bacon

