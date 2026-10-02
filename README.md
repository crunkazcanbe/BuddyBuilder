# Buddy Builder

An AI companion for Minecraft 1.12.2 (Forge / Cleanroom). You type an order, a local language model plans it, and your buddy builds roads, buildings and whole cities out of any block in your modpack.

## About

Buddy Builder adds a menu where you type plain-language orders like "build a pretty modern city around me". The order goes to a language model running on your own computer. The model searches the modpack's blocks, picks ones that fit, and calls the mod's build tools. Blocks are then placed a few hundred per tick, and you can stop or undo at any time.

You can also spawn **friends**: companions with a player body, a name, a skin, a friendship level and a memory of past events, all saved with the world. When you talk to a friend, the model answers in her voice and she walks to the site and builds there. When you talk to "Me", the model controls your own character instead, and can use Baritone to walk and mine.

It was built for the Pride modpack, a 1.12.2 pack with about 750 mods, so that the AI can make use of all those modded blocks (asphalt, concrete, marble, street lamps). It also works in other packs, and with vanilla blocks only.

**It needs a local AI server.** The mod does not include a model. It talks to any OpenAI-compatible chat-completions endpoint that supports tool calling, and by default that endpoint is on `127.0.0.1` (localhost). Nothing is sent to an online AI service unless you change the endpoint yourself.

## Features

### Ordering builds
- **Free-text orders**: type anything in the box and press **Build!** or Enter.
- **Quick builds** fill in an order for you from the current Style and Size settings and the way you are facing:

  | Button | Order sent |
  |---|---|
  | Road | A road 20/40/80 blocks long (by size) going the way you face, with sidewalks and street lamps |
  | Avenue | A wide avenue with fancy sidewalks and lots of lamps |
  | City | A city around you with a road grid, sidewalks, lamps and colourful buildings |
  | Tower | A skyscraper next to you |
  | House | A house next to you |
  | Shops | A row of shops along a road, each a different colour |

- **Styles**: modern, medieval, futuristic, cozy, fancy, industrial. **Sizes**: small, medium, huge.
- **Build speed**: Slow (20 blocks/tick), Normal (200), Fast (1000), Insane (5000).
- **Stop** ends the AI's thinking and clears the build queue.
- **Undo** puts back everything the last order changed. If that order is still building, it is cancelled first. The last 20 orders can be undone one by one.
- **Live log** ("What Buddy is doing") with mouse-wheel scrolling, a progress bar of placed/total blocks, and a status line in the top-left of the HUD while Buddy is working with the menu closed.
- The menu does not pause the game, so building continues while it is open. Your unsent text is kept when you close it.

### Build tools
- **Roads** follow the terrain, with at most one block up or down per step. They clear 4 blocks of headroom, fill gaps up to 3 blocks below with dirt, and can have a sidewalk edge and lamps on 3-block wall posts on both sides.
- **Buildings** are 4–64 blocks wide and deep with 1–30 floors (4 blocks per floor). They have a floor slab on every level, alternating windows, a roof, a door opening and foundations down slopes.
- **Cities** are 1–8 city blocks across (block size 10–48, road width 3–9). They get a full road grid with sidewalks and a lamp every 12 blocks, plus four buildings of 2–6 floors per city block, cycling through the wall blocks the AI picked.
- **Any block**: a block is given as `mod:block` or `mod:block:meta`. If a block does not exist, the tool falls back to stone bricks, bricks, glass or stone slabs.

### Friends
- **New friend** spawns a companion in front of you, named with the text in the box (default "Buddy", up to 24 characters). Friends never despawn and are saved with the world.
- **Talk to** cycles through Me and every loaded friend, nearest first.
- **Skins**: type a Minecraft username and press **Skin**. The skin is downloaded from Mojang once and saved to `config/buddybuilder/skins/<name>.png`, so it never changes afterwards. You can also drop any 64×64 PNG in that folder and type its name. A file `<name>.slim` next to it selects the slim arm model. With no skin set, the friend uses the default Alex skin.
- **Modes**: Follow (walks after you and teleports if you are more than 32 blocks away), Stay, and Wander (the AI can set this one). **Come here** brings the selected friend next to you and sets her to follow.
- **Friendship** ranges from −100 to 1000 points. Each order gives +1, a gift gives +5, and hitting her gives −10. Titles: just met, getting to know you (10), friend (50), close friend (150), best friend (400).
- **Gifts**: right-click a friend with any item to give it to her. You get hearts and a thank-you message, and the item is used up unless you are in creative mode.
- **Memories**: each friend keeps her last 40 memories (meeting you, gifts, being hit, your orders, things the AI chose to remember). They are fed to the model every time you talk to her.
- Friends have 40 health and are drawn with a player model and their name tag always visible.

### AI tools
The model can call these tools. It may take up to 16 steps per order.

| Tool | What it does |
|---|---|
| `find_blocks` | Searches every registered block by id and display name (all words must match). Returns up to 12 ids. |
| `get_position` | Your x, y, z and facing. |
| `build_road` | Road from (x1,z1) to (x2,z2) with width, surface, optional edge, lamp and lamp spacing. |
| `build_building` | Building at a corner (x,z) with width, depth, floors, wall, window and roof blocks. |
| `build_city` | City centred on (x,z) with size, block size, road width, road/sidewalk/lamp blocks, a list of wall blocks, window and roof. |
| `say` | Writes a message to the log and ends the order. |
| `remember` | Saves a fact. With a friend selected, it goes into her memory. Otherwise it is appended to `handoff.md`. |
| `follow_me` / `stay_here` / `wander` | Sets the friend's mode. |
| `go_to` | Moves the friend to (x,z), or walks your own character there with Baritone (`#goto`). |
| `mine` | Your own character mines a block type with Baritone (`#mine`). |
| `stop_walking` | Stops Baritone (`#stop`). |

To keep long jobs inside the model's context, older tool results are shortened. `<think>` blocks in replies are hidden.

### Notes file and log
- `config/buddybuilder/handoff.md` is a notes file. Its last 6,000 characters are added to the model's instructions on every order, so you (or another assistant) can leave plans there for Buddy to continue. The **Claude's plan** button sends an order telling Buddy to read those notes and carry on where they leave off.
- `config/buddybuilder/buddylog.txt` gets every log line with a timestamp, so you can review what Buddy did.

## Opening the menu

| Way | Details |
|---|---|
| **F10** | Keybind "Open Buddy Builder" in the "Buddy Builder" category. You can rebind it in Controls. |
| Inventory | A small pink **B** button to the left of the inventory. |
| Pause menu | **✦ Buddy Builder** button in the top-left corner. |

## Commands

None. Everything is done from the menu.

## Config

File: `config/buddybuilder.cfg`

| Category | Key | Default | Effect |
|---|---|---|---|
| `ai` | `endpoint` | `http://127.0.0.1:8096/v1/chat/completions` | OpenAI-compatible chat-completions URL. Point it at your local server. |
| `ai` | `modelCpu` | `buddy-cpu` | Model name sent when the Brain setting is CPU. |
| `ai` | `modelGpu` | `buddy-gpu` | Model name sent when the Brain setting is GPU. |
| `ai` | `useGpu` | `true` | Which model to use. The **Brain** button in the menu toggles it and saves the choice. Set it to `false` on PCs without a graphics card. |
| `build` | `blocksPerTick` | `200` | Blocks placed per server tick (1–5000). Opening the menu replaces this with the Speed setting there, which defaults to Normal (200). |

Requests use temperature 0.3 and up to 1,500 tokens per reply, with a 5-second connect timeout and a 10-minute read timeout.

## Requirements

- Minecraft 1.12.2
- Forge 14.23.5.2860 or newer, or Cleanroom
- **A local OpenAI-compatible AI server with tool/function calling**, for example llama.cpp's server, llama-swap, LM Studio, Ollama's `/v1` endpoint or vLLM. Either serve models named `buddy-cpu` / `buddy-gpu`, or change the model names in the config. A small instruction-tuned model with good tool calling is enough.
- **Single-player only** for now. Building and friends run on the integrated server.
- Optional: **Baritone**, for the `go_to` (on yourself), `mine` and `stop_walking` tools.
- An internet connection only if you want skins downloaded by username.

## Install

1. Download `BuddyBuilder-1.12.2-<version>.jar` and put it in your `mods/` folder.
2. Start your local AI server, and make sure the model names match the config.
3. Launch the game once, then edit `config/buddybuilder.cfg` if your endpoint is not `http://127.0.0.1:8096/v1/chat/completions`.
4. In a single-player world, press **F10** and type an order.

## Building

```sh
./gradlew build
```

The jar is written to `build/libs/`. The build targets Java 8.

## License

MIT License. © 2026 crunkazcanbe.

## Credits

Made by crunkazcanbe, with Claude.
