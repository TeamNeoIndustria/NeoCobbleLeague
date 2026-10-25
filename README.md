# NeoCobbleLeague

NeoCobbleLeague adds server-run events to Cobblemon on NeoForge 1.21.1. Player gyms are planned but not written yet. Everything described below is what exists today.

It needs Cobblemon 1.7.3, Kotlin for Forge, and NeoCobbleLib 1.1.0 or newer.

## Events

An event is a legendary or important Pokemon, or a trainer, that appears at a fixed place in the world for a limited time. Players find it, fight or catch it, and the first to catch or beat it ends the event and can be rewarded.

Each event goes through the same stages. An admin (or nothing else, for now) starts it, a countdown runs with an announcement in chat, the Pokemon or trainer spawns with a spawn message, and then it stays until something ends it. The ways it can end are:

- CAPTURED: a player caught the Pokemon, with or without a battle.
- DEFEATED: a player fainted the Pokemon or beat the trainer.
- TIMED_OUT: nobody won before the lifetime ran out.
- DESPAWNED: the Pokemon or trainer disappeared some other way, for example it was killed with a command. This is noticed after about five seconds.
- CANCELLED: an admin stopped it, or the server shut down.
- SPAWN_FAILED: the spawn point stopped being usable before the spawn.

Losing to the Pokemon or trainer, or running from it, does not end the event. A trainer that beat a player is healed so the next player gets a full fight. Forfeiting a trainer fight counts as a loss.

Only one copy of each event can run at a time. After an event ends there is a cooldown before it can start again, except when it was cancelled or failed to spawn. The cooldown is kept in memory, so it resets when the server restarts.

Events never survive a restart. When the server stops, active events are cancelled and their Pokemon or trainer is removed. If the server crashes instead, any leftover event Pokemon or trainer is removed when it next loads.

If an event ends while a player is still fighting its trainer, the battle is stopped so the player is not left stuck on the battle screen.

## Players

For a Pokemon event, the Pokemon is a normal wild Pokemon that does not despawn. Players fight and catch it the usual way. Its level is rolled from the event's level range when the event starts.

For a trainer event, players right-click the trainer to start the fight. The trainer cannot be hurt. Trainer parties come from NeoCobbleLib's trainers.json, and every spawn gets fresh copies of the party so damage does not carry over between fights.

## Commands

All of these need permission level 2 and can be used from the console.

    /leagueevent list
    /leagueevent start <id>
    /leagueevent start <id> force
    /leagueevent stop <id>
    /leagueevent skip <id>
    /leagueevent reload

start refuses an event that is disabled, on cooldown, already running, or has no valid spawn point. Adding force ignores the disabled flag and the cooldown. stop cancels a running event. skip ends the countdown early so the event spawns now. reload reads events.json again. Event ids tab-complete.

## Configuration

The file is config/NeoCobbleLeague/events.json. If it is empty, the mod writes two disabled examples, one Pokemon event and one trainer event. Edit them, set Enabled to true, and run /leagueevent reload. A broken event is skipped with a message in the log and does not stop the others from loading.

A Pokemon event looks like this:

    {
      "Id": "example_articuno",
      "DisplayName": "&bArticuno",
      "Enabled": true,
      "Type": "POKEMON",
      "CountdownSeconds": 120,
      "LifetimeSeconds": 900,
      "CooldownSeconds": 3600,
      "Weight": 1,
      "Species": "articuno",
      "LevelRange": [60, 70],
      "SpawnMode": "FIXED",
      "SpawnPoints": [
        { "Dimension": "minecraft:overworld", "X": 0.5, "Y": 100.0, "Z": 0.5 }
      ],
      "Messages": {
        "Announce": "&e{name} has been spotted nearby!",
        "Spawn": "&c{name} has appeared!",
        "End": {
          "CAPTURED": "&a{player} caught {name}&a!",
          "DEFEATED": "&a{player} defeated {name}&a!",
          "TIMED_OUT": "{name} &7disappeared into the distance."
        }
      },
      "Rewards": {
        "CAPTURED": ["give {player} cobblemon:ultra_ball 5"],
        "DEFEATED": ["give {player} cobblemon:rare_candy 3"]
      }
    }

A trainer event uses "Type": "TRAINER" and "TrainerName" instead of Species and LevelRange. TrainerName must match a trainer in NeoCobbleLib's trainers.json. Trainer events have no CAPTURED message or reward.

Notes on the fields:

- Id is required and must be unique. DisplayName defaults to the id.
- Times are in seconds and can be decimals. Defaults are a 120 second countdown, 900 second lifetime and 3600 second cooldown. Lifetime must be above zero. A countdown of zero spawns the event immediately.
- Species uses the names from NeoCobbleLib's species list, in lowercase. LevelRange is two whole numbers, minimum and maximum, between 1 and 100.
- SpawnMode only supports FIXED. If there are several spawn points, one usable point is picked at random each time. A point is skipped if it is outside the world border or height limits, in lava, or blocked by solid blocks. The point is checked again when the countdown ends.
- Weight is read but not used yet. It is meant for automatic event selection later.
- Messages use & for colour codes. {name} is the event's display name and {player} is the winner. Before there is a winner, {player} shows as "someone". Any message left out is not sent.
- Rewards are console commands run when an event ends with a winner, so only CAPTURED and DEFEATED mean anything. {player} is replaced with the winner's name. A leading slash is optional. A command that fails is logged and the rest still run, so check the console after trying a new reward. Because the commands run as the console, they can call anything on the server, such as an economy plugin, as long as the player is passed by name.
- Any key starting with an underscore, such as "_comment", is ignored.

## Not done yet

Player gyms, events starting automatically on a schedule, and saving cooldowns across restarts.