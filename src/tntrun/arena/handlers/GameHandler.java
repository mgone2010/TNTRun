/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */

package tntrun.arena.handlers;

import java.util.ArrayList;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import tntrun.TNTRun;
import tntrun.arena.Arena;
import tntrun.arena.structure.Kits;
import tntrun.utils.Bars;
import tntrun.messages.Messages;

public class GameHandler {

	private TNTRun plugin;
	private Arena arena;

	public GameHandler(TNTRun plugin, Arena arena) {
		this.plugin = plugin;
		this.arena = arena;
		count = arena.getStructureManager().getCountdown();
	}

	private Scoreboard scoreboard = buildScoreboard();

	// arena leave handler
	private int leavetaskid;

	public void startArenaAntiLeaveHandler() {
		leavetaskid = Bukkit.getScheduler().scheduleSyncRepeatingTask(
			plugin,
			new Runnable() {
				@Override
				public void run() {
					for (Player player : arena.getPlayersManager().getPlayersCopy()) {
						if (!arena.getStructureManager().isInArenaBounds(player.getLocation())) {
							arena.getPlayerHandler().leavePlayer(player, Messages.playerlefttoplayer, Messages.playerlefttoothers);
						}
					}
					for (Player player : arena.getPlayersManager().getSpectatorsCopy()) {
						if (!arena.getStructureManager().isInArenaBounds(player.getLocation())) {
							arena.getPlayerHandler().leavePlayer(player, "", "");
						}
					}
				}
			},
			0, 1
		);
	}

	public void stopArenaAntiLeaveHandler() {
		Bukkit.getScheduler().cancelTask(leavetaskid);
	}

	// arena start handler (running status updater)
	int runtaskid;
	int count;

	public void runArenaCountdown() {
		arena.getStatusManager().setStarting(true);
		runtaskid = Bukkit.getScheduler().scheduleSyncRepeatingTask(
			plugin,
			new Runnable() {
				@Override
				public void run() {
					// check if countdown should be stopped for some various reasons
					if (arena.getPlayersManager().getPlayersCount() < arena.getStructureManager().getMinPlayers()) {
						for (Player player : arena.getPlayersManager().getPlayers()) {
							Bars.setBar(player, Bars.waiting, arena.getPlayersManager().getPlayersCount(), 0, arena.getPlayersManager().getPlayersCount() * 100 / arena.getStructureManager().getMinPlayers());
							createWaitingScoreBoard();
						}
						stopArenaCountdown();
					} else
					// start arena if countdown is 0
					if (count == 0) {
						stopArenaCountdown();
						startArena();
					} else if (count < 11) {
						String message = Messages.arenacountdown;
						message = message.replace("{COUNTDOWN}", String.valueOf(count));
						for (Player player : arena.getPlayersManager().getPlayers()) {
							Messages.sendMessage(player, message);
							player.playSound(player.getLocation(), Sound.CLICK, 1, 5);
						}
					}
					if(count == 5) {
						for (Player player : arena.getPlayersManager().getPlayers()) {
							player.teleport(arena.getStructureManager().getSpawnPoint());
						}
					}
					// scoreboard
					createWaitingScoreBoard();
					// sending bars
					for (Player player : arena.getPlayersManager().getPlayers()) {
						player.setLevel(count);
						Bars.setBar(player, Bars.starting, 0, count, count * 100 / arena.getStructureManager().getCountdown());
				    }
					count--;
				}
			},
			0, 20
		);
	}

	public void stopArenaCountdown() {
		arena.getStatusManager().setStarting(false);
		count = arena.getStructureManager().getCountdown();
		Bukkit.getScheduler().cancelTask(runtaskid);
	}

	// main arena handler
	private int timelimit;
	private int arenahandler;
	private int playingtask;

	Random rnd = new Random();

	public void startArena() {
		arena.getStatusManager().setRunning(true);
		String message = Messages.arenastarted;
		message = message.replace("{TIMELIMIT}", String.valueOf(arena.getStructureManager().getTimeLimit()));
		for (Player player : arena.getPlayersManager().getPlayers()) {
			Messages.sendMessage(player, message);
			player.playSound(player.getLocation(), Sound.ENDERDRAGON_GROWL, 1, 1);
		}
		plugin.signEditor.modifySigns(arena.getArenaName());
		Kits kits = arena.getStructureManager().getKits();
		if (kits.getKits().size() > 0) {
			String[] kitnames = kits.getKits().toArray(new String[kits.getKits().size()]);
			for (Player player : arena.getPlayersManager().getPlayers()) {
				kits.giveKit(kitnames[rnd.nextInt(kitnames.length)], player);
			}
		}
		resetScoreboard();
		createPlayingScoreBoard();
		timelimit = arena.getStructureManager().getTimeLimit() * 20; // timelimit is in ticks
		arenahandler = Bukkit.getScheduler().scheduleSyncRepeatingTask(
			plugin,
			new Runnable() {
				@Override
				public void run() {
					// stop arena if player count is 0
					if (arena.getPlayersManager().getPlayersCount() == 0) {
						// stop arena
						stopArena();
						return;
					}
					// kick all players if time is out
					if (timelimit < 0) {
						for (Player player : arena.getPlayersManager().getPlayersCopy()) {
							arena.getPlayerHandler().leavePlayer(player,Messages.arenatimeout, "");
						}
						return;
					}
					// handle players
					for (Player player : arena.getPlayersManager().getPlayersCopy()) {
						// Xp level
						player.setLevel(timelimit/20);
						// update bar
						Bars.setBar(player, Bars.playing, arena.getPlayersManager().getPlayersCount(), timelimit / 20, timelimit * 5 / arena.getStructureManager().getTimeLimit());
						// handle player
						handlePlayer(player);
					}
					// update bars for spectators too
					for (Player player : arena.getPlayersManager().getSpectators()) {
						Bars.setBar(player, Bars.playing, arena.getPlayersManager().getPlayersCount(), timelimit / 20, timelimit * 5 / arena.getStructureManager().getTimeLimit());
					}
					// decrease timelimit
					timelimit--;
				}
			},
			0, 1
		);
	}

	public void stopArena() {
		resetScoreboard();
		for (Player player : arena.getPlayersManager().getAllParticipantsCopy()) {
			arena.getPlayerHandler().leavePlayer(player, "", "");
		}
		arena.getStatusManager().setRunning(false);
		Bukkit.getScheduler().cancelTask(arenahandler);
		Bukkit.getScheduler().cancelTask(playingtask);
		plugin.signEditor.modifySigns(arena.getArenaName());
		if (arena.getStatusManager().isArenaEnabled()) {
			startArenaRegen();
		}
	}

	// player handlers
	public void handlePlayer(final Player player) {
		Location plloc = player.getLocation();
		Location plufloc = plloc.clone().add(0, -1, 0);
		// remove block under player feet
		arena.getStructureManager().getGameZone().destroyBlock(plufloc, arena);
		// check for win
		if (arena.getPlayersManager().getPlayersCount() == 1) {
			// last player won
			arena.getPlayerHandler().leaveWinner(player, Messages.playerwontoplayer);
			broadcastWin(player);
			return;
		}
		// check for lose
		if (arena.getStructureManager().getLoseLevel().isLooseLocation(plloc)) {
			// if we have the spectate spawn than we will move player to spectators, otherwise we will remove him from arena
			if (arena.getStructureManager().getSpectatorSpawnVector() != null) {
				arena.getPlayerHandler().spectatePlayer(player, Messages.playerlosttoplayer, Messages.playerlosttoothers);
			} else {
				arena.getPlayerHandler().leavePlayer(player, Messages.playerlosttoplayer, Messages.playerlosttoothers);
			}
			return;
		}
	}

	private void broadcastWin(Player player) {
		String message = Messages.playerwonbroadcast;
		message = message.replace("{PLAYER}", player.getName());
		message = message.replace("{ARENA}", arena.getArenaName());
		Messages.broadcastMessage(message);
	}

	static Scoreboard buildScoreboard() {
		Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

		Objective o = scoreboard.registerNewObjective("TNTRun", "waiting");
		o.setDisplaySlot(DisplaySlot.SIDEBAR);
		o.setDisplayName("§7[§6TNTRun§7]");

		return scoreboard;
	}
	
	public void createWaitingScoreBoard() {
		resetScoreboard();
		Objective o = scoreboard.getObjective(DisplaySlot.SIDEBAR);
		o.getScore("§6Players").setScore(5);
		o.getScore("§c"+arena.getPlayersManager().getPlayersCount() + "§1").setScore(4);
		o.getScore(" ").setScore(3);
		o.getScore("§6Starting in").setScore(2);
		o.getScore("§c" + count + "§2").setScore(1);
		for (Player p : arena.getPlayersManager().getPlayers()) {
			p.setScoreboard(scoreboard);
		}
	}

	public void resetScoreboard() {
		for (String entry : new ArrayList<String>(scoreboard.getEntries())) {
			scoreboard.resetScores(entry);
		}
	}

	public void createPlayingScoreBoard() {
		playingtask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
			public void run() {
				resetScoreboard();
				Objective o = scoreboard.getObjective(DisplaySlot.SIDEBAR);
				o.getScore("§6Players").setScore(8);
				o.getScore("§c"+arena.getPlayersManager().getPlayersCount() + "§1").setScore(7);
				o.getScore("§1 ").setScore(6);
				o.getScore("§6Spectators").setScore(5);
				o.getScore("§c"+arena.getPlayersManager().getSpectators().size() + "§2").setScore(4);
				o.getScore("§2 ").setScore(3);
				o.getScore("§6Ending in").setScore(2);
				o.getScore("§c" + timelimit/20 + "§3").setScore(1);
			}
		}, 0, 20);
	}

	private void startArenaRegen() {
		// set arena is regenerating status
		arena.getStatusManager().setRegenerating(true);
		// modify signs
		plugin.signEditor.modifySigns(arena.getArenaName());
		// schedule gamezone regen
		int delay = arena.getStructureManager().getGameZone().regen(arena.plugin);
		// regen finished
		Bukkit.getScheduler().scheduleSyncDelayedTask(
			arena.plugin,
			new Runnable() {
				@Override
				public void run() {
					// set not regenerating status
					arena.getStatusManager().setRegenerating(false);
					// modify signs
					plugin.signEditor.modifySigns(arena.getArenaName());
				}
			},
			delay
		);
	}

}
