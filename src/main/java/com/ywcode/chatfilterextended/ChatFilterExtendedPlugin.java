package com.ywcode.chatfilterextended;

import com.google.inject.Provides;

import javax.annotation.*;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.clan.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.*;
import net.runelite.client.config.*;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.*;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.*;

import java.util.*;

@Slf4j
@PluginDescriptor(
		name = "Chat Filter Extended",
		description = "Adds the ability to filter all chat messages not coming from friends/clanmates/FC members/Guest CC members/raid members.",
		tags = {"public,chat,cc,fc,clanchat,clan,filter,friendschat,friends,raids"}
)
//Alternative shitty names: Custom Chat View, Chat View Extended, Chat Show Custom, Custom Chat Filter

public class ChatFilterExtendedPlugin extends Plugin {

	// ------------- Wall of config vars -------------
	private static boolean showFriendsMessages;
	private static boolean showCCMessages;
	private static boolean showFCMessages;
	private static boolean showGuestCCMessages;
	private static boolean showRaidPartyMessages;
	private static boolean forcePrivateOn;
	private static Set<ChatsToFilter> chatsToFilter = new HashSet<ChatsToFilter>();
	private static boolean publicFilterEnabled;
	private static boolean privateFilterEnabled;
	private static boolean channelFilterEnabled;
	private static boolean clanFilterEnabled;
	private static boolean tradeFilterEnabled;
	// ------------- End of wall of config vars -------------
	private static boolean shuttingDown;
	private static int setPublicChatOnInt = 0;
	private static HashSet<String> channelStandardizedUsernames = new HashSet<String>();
	private static HashSet<String> clanStandardizedUsernames = new HashSet<String>();
	private static HashSet<String> guestClanStandardizedUsernames = new HashSet<String>();
	private static HashSet<String> raidPartyStandardizedUsernames = new HashSet<String>();
	private static final int TOB_BOARD_ID = 50; //N50.0
	private static final int TOP_HALF_TOB_BOARD_CHILDID = 27; //S50.27
	private static final int BOTTOM_HALF_TOB_BOARD_CHILDID = 42; //S50.42
	private static final int TOB_PARTY_INTERFACE_NAMES_CHILDID = 12; //S28.12
	//PM Can probably replace part of the functions by incorporating the info in the Enum and then using a getter, but enfin

	@Inject
	private Client client;

	@Inject
	private ChatFilterExtendedConfig config;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Override
	public void startUp() {
		shuttingDown = false;
		setConfigFirstStart();
		updateConfig();
		setWidgetText();
		setChatsToPublic(false);
		//todo: add icon, license, readme
		//todo: go through problems
	}

	@Override
	public void shutDown() {
		//TODO: check if something needs to be added to startup or shutdown + profilechange / rebuild chatbox?
		//todo: potentially do some of this shit or stop filter when swapping profiles? check how other filter plugins do it
		//todo: find a way to disable filtering mode when disabling all the show friends/show cc etc options are disabled + to change the filter mode when config is changed + change text back in that case (&filter)
		//todo: method volgorde fixen
		//todo: final tests: login, hopping, toggling on/off plugin, toggling settings on/off, opening clan panels, changing to resizable, after npc chatbox maybe, in/after cutscenes like myths guild
		//todo: make code easier/clean-up by adding/changing methods, adding local variables etc

		shuttingDown = true; //Probably not necessary but just to be sure it doesn't fire
		if (client.getGameState() == GameState.LOGGED_IN) {
			clientThread.invoke(() -> {
				//This rebuilds both the chatbox and the pmbox
				client.runScript(ScriptID.SPLITPM_CHANGED);
				client.runScript(178); //[proc,redraw_chat_buttons]
			});
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged) {
		if (configChanged.getGroup().equals("chat-filter-extended")) {
			updateConfig();
			disableFilterWhenShowNone();
			disableFilterWhenRemovedFromSet();
			if (configChanged.getKey().equals("forcePrivateOn")) {
				if (forcePrivateOn) {
					//Set friends to on when forePrivateOn is enabled and chat is custom filtered
					setChatsToPublic(false);
				} else {
					//Set friends status to non filtered and redraw chat buttons to show the current state of friends
					executeSetChatFilterConfig(WidgetInfo.CHATBOX_TAB_PRIVATE, false);
					redrawChatButtons();
				}
			}
		}
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged profileChanged) {
		setConfigFirstStart();
		shuttingDown = false;
		setWidgetText();
		setChatsToPublic(false);
	}

	private void updateConfig() {
		showFriendsMessages = config.showFriendsMessages();
		showCCMessages = config.showCCMessages();
		showFCMessages = config.showFCMessages();
		showGuestCCMessages = config.showGuestCCMessages();
		showRaidPartyMessages = config.showRaidPartyMessages();
		chatsToFilter = config.chatsToFilter();
		forcePrivateOn = config.forcePrivateOn();
		publicFilterEnabled = configManager.getConfiguration("chat-filter-extended","publicFilterEnabled", boolean.class);
		privateFilterEnabled = configManager.getConfiguration("chat-filter-extended","privateFilterEnabled", boolean.class);
		channelFilterEnabled = configManager.getConfiguration("chat-filter-extended","channelFilterEnabled", boolean.class);
		clanFilterEnabled = configManager.getConfiguration("chat-filter-extended","clanFilterEnabled", boolean.class);
		tradeFilterEnabled = configManager.getConfiguration("chat-filter-extended","tradeFilterEnabled", boolean.class);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
			setPublicChatOnInt = 2;
			setWidgetText();
			//todo: potentially only do shit while logged in/or when red click to play screen is gone? check how other plugins filter chat
		}
		if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
			clanStandardizedUsernames.clear();
			//Probably only own CC needed; rest procs event.
			channelStandardizedUsernames.clear();
			guestClanStandardizedUsernames.clear();
			raidPartyStandardizedUsernames.clear();
		}
		//Clear raid party members while hopping because you generally don't care about them anymore after hopping to another world
		if (gameStateChanged.getGameState() == GameState.HOPPING) {
			raidPartyStandardizedUsernames.clear();
		}
	}

	@Subscribe
	public void onFriendsChatChanged(FriendsChatChanged friendsChatChanged) {
		//Remove FC usernames when leaving the FC; also procs when hopping/logging out
		if (!friendsChatChanged.isJoined()) {
			channelStandardizedUsernames.clear();
		}
	}

	@Subscribe
	public void onFriendsChatMemberJoined(FriendsChatMemberJoined friendsChatMemberJoined) {
		//Also procs while joining an FC for all the members currently in it
		//In the case of HashSet, the item isn't inserted if it's a duplicate => so no .contains check beforehand.
		channelStandardizedUsernames.add(Text.standardize(friendsChatMemberJoined.getMember().getName()));
	}

	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged clanChannelChanged) {
		//If left CC => clear own cc usernames HashSet
		if (client.getClanChannel(ClanID.CLAN) == null) {
			clanStandardizedUsernames.clear();
		} else {
			//If joined own CC, get members and add the usernames to HashSet
			if (!clanChannelChanged.isGuest()) {
				List<ClanMember> clanMembers = client.getClanSettings().getMembers();
				for (ClanMember clanMember : clanMembers) {
					clanStandardizedUsernames.add(Text.standardize(clanMember.getName()));
				}
			}
		}

		//Also include GIM members in Clan Hashset, untested because no access to a GIM account
		if (client.getClanChannel(ClanID.GROUP_IRONMAN) != null) {
			if (clanChannelChanged.getClanId() == ClanID.GROUP_IRONMAN) {
				List<ClanChannelMember> gimMembers = clanChannelChanged.getClanChannel().getMembers();
				for (ClanChannelMember gimMember : gimMembers) {
					clanStandardizedUsernames.add(Text.standardize(gimMember.getName()));
				}
			}
		}

		//If left guest CC => clear guest cc usernames HashSet
		if (client.getGuestClanChannel() == null) {
			guestClanStandardizedUsernames.clear();
		} else {
			//If joined guest clan, get members and add the usernames to HashSet
			if (clanChannelChanged.isGuest()) {
				List<ClanMember> guestClanMembers = client.getGuestClanSettings().getMembers();
				for (ClanMember guestClanMember : guestClanMembers) {
					guestClanStandardizedUsernames.add(Text.standardize(guestClanMember.getName()));
				}
			}
		}
	}

	@Subscribe
	public void onClanMemberJoined(ClanMemberJoined clanMemberJoined) {
		//getClanSettings.GetMembers won't include any guests; add these this way
		//In the case of HashSet, the item isn't inserted if it's a duplicate => so no .contains check beforehand.
		String parsedJoinedName = Text.removeTags(clanMemberJoined.getClanMember().getName());
		ClanChannel clanChannel = client.getClanChannel();
		ClanChannel guestClanChannel = client.getGuestClanChannel();

		//If username of joined clanmember is in the cc, add to HashSet
		if (clanChannel != null && clanChannel.findMember(parsedJoinedName) != null) {
			clanStandardizedUsernames.add(Text.standardize(parsedJoinedName));
		}

		//If username of joined clanmember is in the guest cc, add to HashSet
		if (guestClanChannel != null && guestClanChannel.findMember(parsedJoinedName) != null) {
			clanStandardizedUsernames.add(Text.standardize(parsedJoinedName));
		}
	}

	//TODO: raids members + board/applicants (zie hieronder)
	//todo: when to clear shit, e.g. raids when hopping/logged out or when visiting other raid/when leaving fc when at cox, use int to determine what activeraid is so you can clear raid usernames and set int to other thingy when entering zone of other raid
	//todo: raid: based on varbit or chunk at cox probs (copy fc if member is in same world or maybe the raid interface if that works in the raid itself, check core cox plugin) (also chunk at tob/toa probs for setting int cause of lobby location or justb do that via widghet idk), tob applicants but also when you apply, widget top left when in lobby and when in raid, toa same as tob. check tob healthbar plugin probs


	@Subscribe
	public void onCommandExecuted(CommandExecuted commandExecuted) { //TEST
		if (commandExecuted.getCommand().contains("test") && !commandExecuted.getCommand().contains("test2")) {
			clientThread.invokeLater(() -> {
				int idx = commandExecuted.getCommand().indexOf(":");
				if (idx != -1) {
					idx++;
					String test = commandExecuted.getCommand().substring(idx);
					int idx2 = test.indexOf(",");
					if (idx2 != -1) {
						idx2++;
						String test2 = test.substring(idx2);
						test = test.substring(0, test.length() - 2);
						System.out.println("running client.runScript(152," + test + "," + test2 + ")");
						client.setVarbit(928, 0);
						client.runScript(152, Integer.parseInt(test), Integer.parseInt(test2));
						client.setVarbit(928, 0);
						//client.runScript(152, 2, 0); public
						//3, 0 for private
						//4, 0 for channel
						//5, 0 for clan
						//6, 0 for trade
					}
				}
			});
		}
		if (commandExecuted.getCommand().contains("test2")) {
			processToBPartyInterface();
		}
	}

	@Subscribe(priority = -4) // run after ChatMessageManager, core ChatFilterPlugin etc
	public void onChatMessage(ChatMessage chatMessage) {
		//todo: add chat filter!
	}

	@Subscribe(priority = -2) //After chatfilter plugin etc, probably not necessary but can't hurt
	public void onOverheadTextChanged(OverheadTextChanged overheadTextChanged) {
		//todo: add chat filter when public filter is enabled!
	}

	@Subscribe(priority = -2) //After chatfilter plugin etc, probably not necessary but can't hurt
	public void onMenuEntryAdded(MenuEntryAdded menuEntryAdded) {
		if (menuEntryAdded.getType() != MenuAction.CC_OP.getId()) {
			return;
		}

		//getActionParam1() seems to be getMenuEntry().getParam1() which seems to be getMenuEntry().getWidget().getId() = 10616843 (for public chat).
		//Only show MenuEntry when ShoulFilterChatType && one of the filter options is enabled
		int menuEntryAddedParam1 = menuEntryAdded.getActionParam1();
		if (shouldFilterChatType(widgetIDtoWidgetInfo(menuEntryAddedParam1)) && menuEntryAdded.getOption().contains("Show none") &&
				(showFriendsMessages || showCCMessages || showFCMessages || showGuestCCMessages || showRaidPartyMessages)) {
			//create MenuEntry and set it's params
			final MenuEntry chatFilterEntry = client.createMenuEntry(-1).setType(MenuAction.RUNELITE_HIGH_PRIORITY);
			chatFilterEntry.setParam1(menuEntryAddedParam1).onClick(this::enableChatFilter);

			//Set name of entry
			final StringBuilder optionBuilder = new StringBuilder();
			//Pull tab name from menu since Trade/Group is variable + set Option based on config settings
			String option = menuEntryAdded.getOption();
			int idx = option.indexOf(':');
			if (idx != -1) {
				optionBuilder.append(option, 0, idx).append(":</col> ");
			}
			optionBuilder.append("Show ");
			if (showFriendsMessages) {
				optionBuilder.append("Friends/");
			}
			if (showCCMessages) {
				optionBuilder.append("CC/");
			}
			if (showFCMessages) {
				optionBuilder.append("FC/");
			}
			if (showGuestCCMessages) {
				optionBuilder.append("Guest/");
			}
			if (showRaidPartyMessages) {
				optionBuilder.append("Raid/");
			}
			optionBuilder.deleteCharAt(optionBuilder.length() - 1); //Remove the trailing /
			chatFilterEntry.setOption(optionBuilder.toString());
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked menuOptionClicked) {
		final String menuOption = menuOptionClicked.getMenuOption();
		//The menu option for show friends is "<col=ffff00>Public:</col> Show friends"
		//First check if it's a valid chatfilter option, then check that it's not the one we inserted => disable chat filtering if user clicked e.g. Public: Show friends
		if (menuOption.contains("<col=ffff00>") && menuOption.contains("Show") && //alternatively widgetIDtoWidgetInfo(menuOptionClicked.getParam1()) != null instead of menuOption.contains("<col=ffff00>") && menuOption.contains("Show") but this is probably less demanding?
				!menuOption.contains("Friends") && !menuOption.contains("CC") && !menuOption.contains("FC") && !menuOption.contains("Guest") && !menuOption.contains("Raid")) { //Plugin uses Friends instead of friends (osrs game)
			//If the specific chat is filtered, disable the filter. Technically the if could potentially be skipped.
			if (isChatFilteredWidgetInfo(widgetIDtoWidgetInfo(menuOptionClicked.getParam1()))) {
				setChatFilterConfig(menuOptionClicked.getMenuEntry(), false);
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick) {
		if (setPublicChatOnInt > 0) {
			setChatsToPublic(false);
			setPublicChatOnInt--;
		}
	}

	//todo: re-enable chat history plugin after testing
	//todo: remove system.out.println & //test

	@Subscribe
	public void onScriptPostFired(ScriptPostFired scriptPostFired) {
		int scriptPostFiredId = scriptPostFired.getScriptId();
		if (scriptPostFiredId == 178 && !shuttingDown) { //178 = [proc,redraw_chat_buttons]
			setWidgetText();
		}
		/*
		//TEST
		Set <Integer> ScriptIds = Stream.of(2316,2317,2319,2320,2321,2329,2333,2334,2335,2336,2337,2338,2339,2340,2341,2342,2343,4490,4491,4492,4493,4495).collect(Collectors.toSet());

		//2332 procs a ton and is part of the refreshing graph
		if (ScriptIds.contains(scriptPostFired.getScriptId())) {
		//System.out.println(scriptPostFired.getScriptId());
		}
		*/

		if (scriptPostFiredId == 4495) { //[proc,tob_partydetails_back_button].cs2 seems to trigger when opening party and when applying at the end, after 2317 has procced multiple times to add all the info (2317 proccs once per potential team member to add them to the board interface iirc)
			processToBBoard();
		}
	}
	//TODO: find out what scriptID you wanna use to proc processToBPartyInterface() and test it out
	//TODO: get tob members in raid and add them (idk what script or something does something here, check tob health bar plugin; maybe uses varcs just like toa? check geheur commit here: https://github.com/runelite/runelite/pull/13765/files)
	//TODO: set int or something to 1 when in/at tob, 2 toa, 3 cox (worldpoint for at probs and then varbits for in raid? check cox plugin, tob plugins, toa plugin for varbits. check discord plugin for wordlpoints/regions (although banks are probs missing))
	//todo: reset tob raid list e.g. when entering cox/toa zone, when hopping (already does iirc), on logout (already does iirc), other conditions? clear on disband (probs not though, geeft evt chat message but idk)
	//TODO: test what happens when applying and accepted, test what happens when someone else applies and you accept them, test what happens when someone applies and someone else accepts them and you have screen open/closed, test what happens when someone applies and you are not party leader but open board after applying (not accepted yet), test what happens when someone applies and you are not party leader but have board open while he applies (not accepted yet), test what happens in raid?
	/* scriptIds tob board as reference:
	Standard refresh:
	2316
	2317
	2317
	2317
	2317
	2317
	4495

	apply:
	2316
	2317
	2317
	2317
	2317
	2317
	2321
	2335
	2335
	2335
	2335
	2335
	2335
	2335
	2335
	2335
	2335
	2333
	4495


	open party:
	2316
	2317
	2317
	2317
	2317
	2317
	4495


	open tob board no party:
	2339
	2337
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2340
	2342
	2342
	2342
	2342
	2342
	2342
	2341
	2340
	2339
	2338
	 */

	//todo: add toa; for ingame can probs use these varcs https://discord.com/channels/301497432909414422/419891709883973642/1088984732764749946
	// apparently also added in https://discord.com/channels/301497432909414422/968623039120035850/1089273683454984393 to toa plugin so take a look!

	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged) {
		if ((channelFilterEnabled && varbitChanged.getVarbitId() == 928 && varbitChanged.getValue() != 0) || //928 = FC value, 929 = cc value (int $chatfilter1)
				(clanFilterEnabled && varbitChanged.getVarbitId() == 929 && varbitChanged.getValue() != 0)) {
			setChatsToPublic(true);
		}
	}

	private void disableFilterWhenShowNone() {
		//Disable currently active filters + rebuild chatbuttons when all show config options are disabled
		if (!showFriendsMessages && !showCCMessages && !showFCMessages && !showGuestCCMessages && !showRaidPartyMessages) {
			executeSetChatFilterConfig(WidgetInfo.CHATBOX_TAB_PUBLIC, false);
			executeSetChatFilterConfig(WidgetInfo.CHATBOX_TAB_PRIVATE, false);
			executeSetChatFilterConfig(WidgetInfo.CHATBOX_TAB_CHANNEL, false);
			executeSetChatFilterConfig(WidgetInfo.CHATBOX_TAB_CLAN, false);
			executeSetChatFilterConfig(WidgetInfo.CHATBOX_TAB_TRADE, false);
			redrawChatButtons();
		}
	}

	private void redrawChatButtons() {
		if (client.getGameState() == GameState.LOGGED_IN) {
			clientThread.invokeLater(() -> {
				client.runScript(178); //[proc,redraw_chat_buttons]
			});
		}
	}

	private void disableFilterWhenRemovedFromSet() {
		//Disable currently active filter + rebuild chatbuttons if the chat to filter gets disabled in config
		boolean shouldRedraw = false;
		if (publicFilterEnabled && !chatsToFilter.contains(ChatsToFilter.PUBLIC)) {
			executeSetChatFilterConfig(WidgetInfo.CHATBOX_TAB_PUBLIC, false);
			shouldRedraw = true;
		}
		if (privateFilterEnabled && !chatsToFilter.contains(ChatsToFilter.PRIVATE)) {
			executeSetChatFilterConfig(WidgetInfo.CHATBOX_TAB_PRIVATE, false);
			shouldRedraw = true;
		}
		if (channelFilterEnabled && !chatsToFilter.contains(ChatsToFilter.CHANNEL)) {
			executeSetChatFilterConfig(WidgetInfo.CHATBOX_TAB_CHANNEL, false);
			shouldRedraw = true;
		}
		if (clanFilterEnabled && !chatsToFilter.contains(ChatsToFilter.CLAN)) {
			executeSetChatFilterConfig(WidgetInfo.CHATBOX_TAB_CLAN, false);
			shouldRedraw = true;
		}
		if (tradeFilterEnabled && !chatsToFilter.contains(ChatsToFilter.TRADE)) {
			executeSetChatFilterConfig(WidgetInfo.CHATBOX_TAB_TRADE, false);
			shouldRedraw = true;
		}
		if (shouldRedraw) {
			redrawChatButtons();
		}
	}

	private void setConfigFirstStart() {
		//Config keys are still empty on first startup. Prevent them being null by setting them before other code checks the config keys.
		if (configManager.getConfiguration("chat-filter-extended", "publicFilterEnabled") == null) {
			configManager.setConfiguration("chat-filter-extended", "publicFilterEnabled", false);
		}
		if (configManager.getConfiguration("chat-filter-extended", "privateFilterEnabled") == null) {
			configManager.setConfiguration("chat-filter-extended", "privateFilterEnabled", false);
		}
		if (configManager.getConfiguration("chat-filter-extended", "channelFilterEnabled") == null) {
			configManager.setConfiguration("chat-filter-extended", "channelFilterEnabled", false);
		}
		if (configManager.getConfiguration("chat-filter-extended", "clanFilterEnabled") == null) {
			configManager.setConfiguration("chat-filter-extended", "clanFilterEnabled", false);
		}
		if (configManager.getConfiguration("chat-filter-extended", "tradeFilterEnabled") == null) {
			configManager.setConfiguration("chat-filter-extended", "tradeFilterEnabled", false);
		}
	}

	private boolean shouldFilterChatType(WidgetInfo widgetInfo) {
		//Should the specific chat be filtered conform the config set <chatsToFilter>
		if (widgetInfo == null) {
			return false;
		}
		switch (widgetInfo) {
			case CHATBOX_TAB_PUBLIC:
				return chatsToFilter.contains(ChatsToFilter.PUBLIC);
			case CHATBOX_TAB_PRIVATE:
				return chatsToFilter.contains(ChatsToFilter.PRIVATE);
			case CHATBOX_TAB_CHANNEL:
				return chatsToFilter.contains(ChatsToFilter.CHANNEL);
			case CHATBOX_TAB_CLAN:
				return chatsToFilter.contains(ChatsToFilter.CLAN);
			case CHATBOX_TAB_TRADE:
				return chatsToFilter.contains(ChatsToFilter.TRADE);
		}
		return false;
	}

	@Nullable
	private WidgetInfo widgetIDtoWidgetInfo(int widgetID) {
		//Translate the WidgetID back to WidgetInfo
		//PM When using this method, do a null check beforehand because it might return null!
		int publicTabId = WidgetInfo.CHATBOX_TAB_PUBLIC.getId();
		int privateTabId = WidgetInfo.CHATBOX_TAB_PRIVATE.getId();
		int channelTabId = WidgetInfo.CHATBOX_TAB_CHANNEL.getId();
		int clanTabId = WidgetInfo.CHATBOX_TAB_CLAN.getId();
		int tradeTabId = WidgetInfo.CHATBOX_TAB_TRADE.getId();

		if (widgetID == publicTabId) {
			return WidgetInfo.CHATBOX_TAB_PUBLIC;
		}
		if (widgetID == privateTabId) {
			return WidgetInfo.CHATBOX_TAB_PRIVATE;
		}
		if (widgetID == channelTabId) {
			return WidgetInfo.CHATBOX_TAB_CHANNEL;
		}
		if (widgetID == clanTabId) {
			return WidgetInfo.CHATBOX_TAB_CLAN;
		}
		if (widgetID == tradeTabId) {
			return WidgetInfo.CHATBOX_TAB_TRADE;
		}
		return null;
	}

	private void enableChatFilter(MenuEntry menuEntry) {
		setChatFilterConfig(menuEntry, true);
		setChatsToPublic(false);
		setWidgetText();
	}

	private void setChatFilterConfig(MenuEntry menuEntry, boolean enableFilter) {
		int menuWidgetID = menuEntry.getParam1();
		WidgetInfo widgetInfo = widgetIDtoWidgetInfo(menuWidgetID);
		if (widgetInfo != null) {
			executeSetChatFilterConfig(widgetInfo, enableFilter);
		}
	}

	private void executeSetChatFilterConfig(WidgetInfo widgetInfo, boolean enableFilter) {
		//Separate function so it can be easily run by putting in the WidgetInfo instead having to enter a MenuEntry
		switch (widgetInfo) {
			case CHATBOX_TAB_PUBLIC:
				publicFilterEnabled = enableFilter; //Probs not necessary since next change might trigger updateConfig() but enfin; would have to experiment with this again to confirm.
				configManager.setConfiguration("chat-filter-extended", "publicFilterEnabled", enableFilter);
				break;
			case CHATBOX_TAB_PRIVATE:
				privateFilterEnabled = enableFilter;
				configManager.setConfiguration("chat-filter-extended", "privateFilterEnabled", enableFilter);
				break;
			case CHATBOX_TAB_CHANNEL:
				channelFilterEnabled = enableFilter;
				configManager.setConfiguration("chat-filter-extended", "channelFilterEnabled", enableFilter);
				break;
			case CHATBOX_TAB_CLAN:
				clanFilterEnabled = enableFilter;
				configManager.setConfiguration("chat-filter-extended", "clanFilterEnabled", enableFilter);
				break;
			case CHATBOX_TAB_TRADE:
				tradeFilterEnabled = enableFilter;
				configManager.setConfiguration("chat-filter-extended", "tradeFilterEnabled", enableFilter);
				break;
		}
	}

	private void setChatsToPublic(boolean onlyVolatile) {
		if (client.getGameState() == GameState.LOGGED_IN) {
			//Public, private, trade remember between hops
			//Channel, Clan don't remember between hopping; potentially related to varbits as described here https://discord.com/channels/301497432909414422/301497432909414422/1086022946633547867
			clientThread.invokeLater(() -> {
				if (!onlyVolatile) {
					if (publicFilterEnabled) {
						client.runScript(152, 2, 0);
					}
					if (privateFilterEnabled && forcePrivateOn) {
						client.runScript(152, 3, 0);
					}
					if (tradeFilterEnabled) {
						client.runScript(152, 6, 0);
					}
				}
				if (channelFilterEnabled) {
					client.runScript(152, 4, 0);
				}
				if (clanFilterEnabled) {
					client.runScript(152, 5, 0);
				}
			});
		}
	}

	private void setWidgetText() {
		//Sets the WidgetText for enabled chats to Custom
		if (client.getGameState() != GameState.LOGGED_IN) {
			return;
		}

		Widget publicWidget = client.getWidget(WidgetInfo.CHATBOX_TAB_PUBLIC);
		Widget privateWidget = client.getWidget(WidgetInfo.CHATBOX_TAB_PRIVATE);
		Widget channelWidget = client.getWidget(WidgetInfo.CHATBOX_TAB_CHANNEL);
		Widget clanWidget = client.getWidget(WidgetInfo.CHATBOX_TAB_CLAN);
		Widget tradeWidget = client.getWidget(WidgetInfo.CHATBOX_TAB_TRADE);
		if (publicWidget != null && publicFilterEnabled) {
			setCustomText(publicWidget);
		}
		if (privateWidget != null && privateFilterEnabled) {
			setCustomText(privateWidget);
		}
		if (channelWidget != null && channelFilterEnabled) {
			setCustomText(channelWidget);
		}
		if (clanWidget != null && clanFilterEnabled) {
			setCustomText(clanWidget);
		}
		if (tradeWidget != null && tradeFilterEnabled) {
			setCustomText(tradeWidget);
		}
	}

	private void setCustomText(Widget widget) {
		final String customTextString = "<br><col=ffff00>Custom</col>";
		widget.getStaticChildren()[2].setText(customTextString); //or e.g. chatWidget.getStaticChildren().length-1
	}

	private boolean isChatFilteredWidgetInfo(WidgetInfo widgetInfo) {
		if (widgetInfo != null) {
			switch (widgetInfo) {
				case CHATBOX_TAB_PUBLIC:
					return publicFilterEnabled;
				case CHATBOX_TAB_PRIVATE:
					return privateFilterEnabled;
				case CHATBOX_TAB_CHANNEL:
					return channelFilterEnabled;
				case CHATBOX_TAB_CLAN:
					return clanFilterEnabled;
				case CHATBOX_TAB_TRADE:
					return tradeFilterEnabled;
			}
		}
		return false;
	}

	private boolean shouldFilterMessage(ChatMessageType chatMessageType, String playerName) {
		if (isChatFilteredChatMessageType(chatMessageType)) {
			return shouldFilterMessagePlayerName(playerName);
		}
		return false;
	}

	private boolean isChatFilteredChatMessageType(ChatMessageType chatMessageType) {
		if (chatMessageType != null) {
			switch (chatMessageType) {
				//AUTOTYPER	is filtered on public = on anyway
				case PUBLICCHAT:
				case MODCHAT:
					return publicFilterEnabled;
				case PRIVATECHAT:
				case MODPRIVATECHAT:
					return privateFilterEnabled;
				case FRIENDSCHAT:
					return channelFilterEnabled;
				case CLAN_CHAT:
				case CLAN_GIM_CHAT:
				case CLAN_GUEST_CHAT:
						return clanFilterEnabled;
				case TRADEREQ:
					return tradeFilterEnabled;
			}
		}
		return false;
	}

	private boolean shouldFilterMessagePlayerName(String playerName) {
		playerName = Text.removeTags(playerName);
		if (playerName.equals(client.getLocalPlayer().getName())) {
			return false;
		}
		if (showFriendsMessages && client.isFriended(playerName, false)) {
			return false;
		}
		playerName = Text.standardize(playerName);
		if (showFCMessages && channelStandardizedUsernames.contains(playerName)) {
			return false;
		}
		if (showCCMessages && clanStandardizedUsernames.contains(playerName)) {
			return false;
		}
		if (showGuestCCMessages && guestClanStandardizedUsernames.contains(playerName)) {
			return false;
		}
		if (showRaidPartyMessages && raidPartyStandardizedUsernames.contains(playerName)) {
			return false;
		}
		return true;
	}

	/* Alternatively use
		private boolean isFriendsChatMember(String playerName) {
			FriendsChatManager friendsChatManager = client.getFriendsChatManager();
			return friendsChatManager != null && friendsChatManager.findByName(playerName) != null;
		}

		private boolean isClanChatMember(String playerName) {
			ClanChannel clanChannel = client.getClanChannel();
			if (clanChannel != null && clanChannel.findMember(playerName) != null) {
				return true;
			}
			ClanChannel guestClanChannel = client.getGuestClanChannel();
			if (guestClanChannel != null && guestClanChannel.findMember(playerName) != null) {
				return true;
			}
			return false;
		}

		However, I'd like the usernames to persist until the user logs out or leaves the chat, since sometimes people briefly leave the FC/CC/guest CC and still type etc
		 */

	//private static final int TOB_BOARD_ID = 50; //N50.0
	//private static final int TOP_HALF_TOB_BOARD_CHILDID = 27; //S50.27
	//private static final int BOTTOM_HALF_TOB_BOARD_CHILDID = 42; //S50.42
	//Top part of the ToB board names are dynamic children of 50.27, e.g. D50.27[0], D50.27[1], D50.27[2] etc.
	//[0], [11], [22] etc is the whole line; these are useless for info but always type 3.
	//[1], [12], [23] etc are the usernames. These are type 4.
	//Bottom part of the ToB board names are dynamic children of 50.42, e.g. D50.42[0], D50.42[1], [2], [4], [6], [8], [10], [12], [14], [16], [18], [20], [21], [22], [24]
	//[0] and [20] are the whole lines again, type 3.
	//[1] and [21] are the usernames, type 4.
	private void processToBBoard() {
		Widget topHalfToBBoardWidget = client.getWidget(TOB_BOARD_ID, TOP_HALF_TOB_BOARD_CHILDID);
		Widget bottomHalfToBBoardWidget = client.getWidget(TOB_BOARD_ID, BOTTOM_HALF_TOB_BOARD_CHILDID);
		HashSet<String> raidPartyStandardizedUsernamesTemp = new HashSet<String>();
		if (topHalfToBBoardWidget != null && topHalfToBBoardWidget.getChildren() != null) {
			for (int i = 0; i < topHalfToBBoardWidget.getChildren().length; i++) {
				//Get child that has type 3 => next one has to be name
				if (topHalfToBBoardWidget.getChild(i).getType() == 3) {
					//Index of the one that has name is type 3 index + 1
					int indexName = topHalfToBBoardWidget.getChild(i).getIndex() +1;
					if (topHalfToBBoardWidget.getChild(indexName).getType() == 4) {
						//If right type (4), get the text and sanitize it
						String StandardizedRaidUsername = Text.standardize(topHalfToBBoardWidget.getChild(indexName).getText());
						if (!StandardizedRaidUsername.equals("-") && !StandardizedRaidUsername.equals(" -") && !StandardizedRaidUsername.equals("- ") && !StandardizedRaidUsername.equals(" - ")) { //Skip empty entries and add to temporary HashSet to remember
							raidPartyStandardizedUsernamesTemp.add(StandardizedRaidUsername);
						}
					}
				}
			}
		}
		if (bottomHalfToBBoardWidget != null && bottomHalfToBBoardWidget.getChildren() != null) {
			for (int i = 0; i < bottomHalfToBBoardWidget.getChildren().length; i++) {
				//Get child that has type 3 => next one has to be name
				if (bottomHalfToBBoardWidget.getChild(i).getType() == 3) {
					//Index of the one that has name is type 3 index + 1
					int indexName = bottomHalfToBBoardWidget.getChild(i).getIndex() +1;
					if (bottomHalfToBBoardWidget.getChild(indexName).getType() == 4) {
						//If right type (4), get the text and sanitize it
						String StandardizedRaidUsername = Text.standardize(bottomHalfToBBoardWidget.getChild(indexName).getText());
						if (!StandardizedRaidUsername.equals("-") && !StandardizedRaidUsername.equals(" -") && !StandardizedRaidUsername.equals("- ") && !StandardizedRaidUsername.equals(" - ")) {
							raidPartyStandardizedUsernamesTemp.add(StandardizedRaidUsername);
						}
					}
				}
			}
		}
		//If it's the user's party/the user applied, add to temporary HashSet to the real HashSet
		if (raidPartyStandardizedUsernamesTemp.contains(Text.standardize(client.getLocalPlayer().getName()))) {
			raidPartyStandardizedUsernames.addAll(raidPartyStandardizedUsernamesTemp);
		}
		System.out.println("raidPartyStandardizedUsernamesTemp (Board) = "+ raidPartyStandardizedUsernamesTemp); //TEST
		System.out.println("raidPartyStandardizedUsernames (Board) = "+raidPartyStandardizedUsernames); //TEST
	}

	//private static final int TOB_PARTY_INTERFACE_NAMES_CHILDID = 12; //S28.12
	//No party text = -<br>-<br>-<br>-<br>-
	//Party text = Username<br>-<br>-<br>-<br>-
	private void processToBPartyInterface() {
		Widget tobPartyInterfaceNamesWidget = client.getWidget(WidgetID.TOB_GROUP_ID, TOB_PARTY_INTERFACE_NAMES_CHILDID); //S28.12
		if (tobPartyInterfaceNamesWidget != null) {
			String toBPartyInterfaceText = tobPartyInterfaceNamesWidget.getText();
			toBPartyInterfaceText = toBPartyInterfaceText.concat("<br>"); //Append <br> so indexOf and substring works for every item
			int start;
			for (int i = 0; i < 5; i++) {
				int idx = toBPartyInterfaceText.indexOf("<br>");
				if (idx != -1) {
					String StandardizedUsername = Text.standardize(toBPartyInterfaceText.substring(0, idx));
					if (!StandardizedUsername.equals("-")) {
						//Since the user has to be in this party (can't view other parties like this), add to the real HashSet instead of a temp one
						raidPartyStandardizedUsernames.add(StandardizedUsername);
					}
					start = idx+4; //+4 so substring starts after the first <br>
					toBPartyInterfaceText = toBPartyInterfaceText.substring(start, toBPartyInterfaceText.length()); //get substring to remove first user and first <br>
				}
			}
		}
		System.out.println("raidPartyStandardizedUsernames (Party Interface top left) = "+raidPartyStandardizedUsernames); //TEST
	}

	//todo: make filter code probs (hangt tevens met list etc samen) => voor friends zie chatfilter plugin iig! + test dit ingame
	//todo: Add own displayname + ook wanneer onacchashchanged en die niet null is. Check of tob widget shit ook varplayer kan zijn + denk aan cox gedoe zoals de interface bij fc of mensen die in je raid zijn of zo + separate config options voor chatbox vs overhead + rebuild chatbox enzo als config options aangepast zijn + rebuild als mensen aan de lijst zijn toegevoegd (zijn oude messages van voor de add to list dan ook zichtbaar?)

	@Provides
	ChatFilterExtendedConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(ChatFilterExtendedConfig.class);
	}
}

//====================OLD EXPERIMENTATION=====================

/*
		System.out.println("menuEntryAdded.getMenuEntry() = "+menuEntryAdded.getMenuEntry());
		System.out.println("menuEntryAdded.getIdentifier() = "+menuEntryAdded.getIdentifier());
		System.out.println("menuEntryAdded.getActionParam0() = "+menuEntryAdded.getActionParam0());
		System.out.println("menuEntryAdded.getActionParam1() = "+menuEntryAdded.getActionParam1());
		System.out.println("menuEntryAdded.getOption() = "+menuEntryAdded.getOption());
		System.out.println("menuEntryAdded.getTarget() = "+menuEntryAdded.getTarget());
		System.out.println("menuEntryAdded.getType() = "+menuEntryAdded.getType());
		System.out.println("menuEntryAdded.getMenuEntry().getParent() = "+menuEntryAdded.getMenuEntry().getParent());
		System.out.println("menuEntryAdded.getMenuEntry().getParam0() = "+menuEntryAdded.getMenuEntry().getParam0());
		System.out.println("menuEntryAdded.getMenuEntry().getParam1() = "+menuEntryAdded.getMenuEntry().getParam1());
		System.out.println("menuEntryAdded.getMenuEntry().getWidget() = "+menuEntryAdded.getMenuEntry().getWidget());
		System.out.println("menuEntryAdded.getMenuEntry().getWidget().getId() = "+menuEntryAdded.getMenuEntry().getWidget().getId());
		System.out.println("menuEntryAdded.getMenuEntry().getWidget().getName() = "+menuEntryAdded.getMenuEntry().getWidget().getName());
		System.out.println("menuEntryAdded.getMenuEntry().getWidget().getText() = "+menuEntryAdded.getMenuEntry().getWidget().getText());

		//Public
		menuEntryAdded.getMenuEntry() = MenuEntryImpl(getOption=Switch tab, getTarget=, getIdentifier=1, getType=CC_OP, getParam0=-1, getParam1=10616843, getItemId=-1, isForceLeftClick=false, isDeprioritized=false)
		menuEntryAdded.getIdentifier() = 1
		menuEntryAdded.getActionParam0() = -1
		menuEntryAdded.getActionParam1() = 10616843
		menuEntryAdded.getOption() = Switch tab
		menuEntryAdded.getTarget() =
		menuEntryAdded.getType() = 57
		menuEntryAdded.getMenuEntry().getParent() = null
		menuEntryAdded.getMenuEntry().getParam0() = -1
		menuEntryAdded.getMenuEntry().getParam1() = 10616843
		menuEntryAdded.getMenuEntry().getWidget() = my@427132b1
		menuEntryAdded.getMenuEntry().getWidget().getId() = 10616843
		menuEntryAdded.getMenuEntry().getWidget().getName() =
		menuEntryAdded.getMenuEntry().getWidget().getText() =
		 */

/*
		menuOptionClicked.getMenuOption() = <col=ffff00>Public:</col> Show friends
		menuOptionClicked.getMenuAction() = CC_OP
		menuOptionClicked.getMenuEntry() = MenuEntryImpl(getOption=<col=ffff00>Public:</col> Show friends, getTarget=, getIdentifier=4, getType=CC_OP, getParam0=-1, getParam1=10616843, getItemId=-1, isForceLeftClick=false, isDeprioritized=false)
		menuOptionClicked.getId() = 4
		menuOptionClicked.getMenuTarget() =
		menuOptionClicked.getParam0() = -1
		menuOptionClicked.getWidget() = my@427132b1
		menuOptionClicked.getWidget().getName() =
		menuOptionClicked.getWidget().getId() = 10616843
		menuOptionClicked.getWidget().getText() =
		menuOptionClicked.getMenuEntry().getOption() = <col=ffff00>Public:</col> Show friends
		menuOptionClicked.getMenuEntry().getIdentifier() = 4
		menuOptionClicked.getMenuEntry().getParam0() = -1
		menuOptionClicked.getMenuEntry().getParam1() = 10616843
		menuOptionClicked.getMenuEntry().getParent() = null
		menuOptionClicked.getMenuEntry().getTarget() =
		menuOptionClicked.getMenuEntry().getIdentifier() = 4


		menuOptionClicked.getMenuOption() = <col=ffff00>Public:</col> Clear history
		menuOptionClicked.getMenuAction() = CC_OP_LOW_PRIORITY
		menuOptionClicked.getMenuEntry() = MenuEntryImpl(getOption=<col=ffff00>Public:</col> Clear history, getTarget=, getIdentifier=7, getType=CC_OP_LOW_PRIORITY, getParam0=-1, getParam1=10616843, getItemId=-1, isForceLeftClick=false, isDeprioritized=false)
		menuOptionClicked.getId() = 7
		menuOptionClicked.getMenuTarget() =
		menuOptionClicked.getParam0() = -1
		menuOptionClicked.getWidget() = my@427132b1
		menuOptionClicked.getWidget().getName() =
		menuOptionClicked.getWidget().getId() = 10616843
		menuOptionClicked.getWidget().getText() =
		menuOptionClicked.getMenuEntry().getOption() = <col=ffff00>Public:</col> Clear history
		menuOptionClicked.getMenuEntry().getIdentifier() = 7
		menuOptionClicked.getMenuEntry().getParam0() = -1
		menuOptionClicked.getMenuEntry().getParam1() = 10616843
		menuOptionClicked.getMenuEntry().getParent() = null
		menuOptionClicked.getMenuEntry().getTarget() =
		menuOptionClicked.getMenuEntry().getIdentifier() = 7
		*/