package com.ywcode.chatfilterextended;

import com.google.inject.*;
import lombok.extern.slf4j.*;
import net.runelite.api.*;
import net.runelite.api.clan.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.*;
import net.runelite.client.config.*;
import net.runelite.client.eventbus.*;
import net.runelite.client.events.*;
import net.runelite.client.plugins.*;
import net.runelite.client.util.*;

import javax.annotation.*;
import javax.inject.Inject;
import java.util.*;

@Slf4j
@PluginDescriptor(
		name = "Chat Filter Extended",
		description = "Extends the functionality of the chat tabs/stones to filter chat messages not from friends/clan members/FC members/Guest CC members/raid members.",
		tags = {"public,public 2d,chat,cc,fc,clanchat,clan,filter,friends chat,friends chat,friends,private,trade,raids,chat filter,tob,toa,cox,spam,custom"}
)
//Alternative (shitty) names: Custom Chat View, Chat View Extended, Chat Show Custom, Custom Chat Filter, Chat tabs extended, Chat stones extended

public class ChatFilterExtendedPlugin extends Plugin {

	// ------------- Wall of config vars -------------
	// Vars are quite heavily cached, so I should just use config.configKey() tbh. The List<String>s are actually converted from a normal string though.
	private static Set<ChatTabFilterOptions> publicChatFilterOptions;
	private static Set<ChatTabFilterOptions2D> publicChatFilterOptions2D;
	private static final List<String> publicWhitelist = new ArrayList<>();
	private static Set<ChatTabFilterOptions> privateChatFilterOptions;
	private static final List<String> privateWhitelist = new ArrayList<>();
	private static boolean forcePrivateOn;
	private static Set<ChatTabFilterOptions> channelChatFilterOptions;
	private static final List<String> channelWhitelist = new ArrayList<>();
	private static Set<ChatTabFilterOptions> clanChatFilterOptions;
	private static final List<String> clanWhitelist = new ArrayList<>();
	private static Set<ChatTabFilterOptions> tradeChatFilterOptions;
	private static final List<String> tradeWhitelist = new ArrayList<>();

	private static boolean showFriendsMessages; //todo: probs remove these later on
	private static boolean showCCMessages;
	private static boolean showFCMessages;
	private static boolean showGuestCCMessages;
	private static boolean showRaidPartyMessages;

	//The config values below are only set through ConfigManager and are not part of ChatFilterExtendedConfig.java
	private static boolean publicFilterEnabled; //i.e. if the user set the chat tab/stone to custom. So we can re-enable it on startup. Maybe swap this to RSProfile instead of config profile in the future?
	private static boolean privateFilterEnabled; //i.e. if the user set the chat tab/stone to custom. So we can re-enable it on startup. Maybe swap this to RSProfile instead of config profile in the future?
	private static boolean channelFilterEnabled; //i.e. if the user set the chat tab/stone to custom. So we can re-enable it on startup. Maybe swap this to RSProfile instead of config profile in the future?
	private static boolean clanFilterEnabled; //i.e. if the user set the chat tab/stone to custom. So we can re-enable it on startup. Maybe swap this to RSProfile instead of config profile in the future?
	private static boolean tradeFilterEnabled; //i.e. if the user set the chat tab/stone to custom. So we can re-enable it on startup. Maybe swap this to RSProfile instead of config profile in the future?

	// ------------- End of wall of config vars -------------
	private static boolean shuttingDown;
	private static int setPublicChatOnInt; //Default value for ints = 0
	private static final HashSet<String> channelStandardizedUsernames = new HashSet<>();
	private static final HashSet<String> clanStandardizedUsernames = new HashSet<>();
	private static final HashSet<String> guestClanStandardizedUsernames = new HashSet<>();
	private static final HashSet<String> raidPartyStandardizedUsernames = new HashSet<>();
	private static final List<Integer> chatboxComponentIDs = Arrays.asList(ComponentID.CHATBOX_TAB_PUBLIC, ComponentID.CHATBOX_TAB_PRIVATE, ComponentID.CHATBOX_TAB_CHANNEL, ComponentID.CHATBOX_TAB_CLAN, ComponentID.CHATBOX_TAB_TRADE);
	private static final List<String> filtersEnabledStringList = Arrays.asList("publicFilterEnabled", "privateFilterEnabled", "channelFilterEnabled", "clanFilterEnabled", "tradeFilterEnabled");
	private static final int REDRAW_CHAT_BUTTONS_SCRIPTID = 178; //[proc,redraw_chat_buttons]
	private static final int TOB_BOARD_ID = 50; //N50.0
	private static final int TOP_HALF_TOB_BOARD_CHILDID = 27; //S50.27
	private static final int BOTTOM_HALF_TOB_BOARD_CHILDID = 42; //S50.42
	private static final int TOB_PARTY_INTERFACE_NAMES_CHILDID = 12; //S28.12
	//Collection cheat sheet: https://i.stack.imgur.com/POTek.gif (that I did not fully adhere to lol)
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
		setConfigFirstStart(); //todo: check if this needs more config vars added to it after config changes
		updateConfig();
		setChatStoneWidgetTextAll();
		setChatsToPublic(false);
		//todo: add readme
		//todo: go through problems
		//todo: Change config thing to have different filters per chat so one for public, one for private + add config setting to add only 2d text for some people but not into chatbox? So then it'd only hide the chatbox stuff from those people => only for public chat cause rest is chatbox only... Including randos? So you could e.g. be everyone 2d except friends also chatbox but clan fully filtered? Requires public to also be added to the initial options!
		//todo: make whitelist toCSV. Keep in mind that you also add that to the options, but then also ctrl+F it, because you got some code that looks at it to determine if chat is filtered!
		//todo: check and refactor the whole fucking shit + check if you can replace widget crawling with varcstrings etc
		//todo: mss per region settings in advanced doch mss gaat dit te ver + add chat message mss wanneer je iets op custom zet (doch mss wat te spammy) maar def als je private verandert dat het on is! (En mss wrm het niet werkt als je die setting niet enabled hebt?) => echter probs te spammy wat support traffic geeft, dus probs skippen want de config setting is heel duidelijk
		//todo: potentially add player right-click option (if a config option is enabled, maybe also option to make this only when shift is held down?) to add to a whitelist with a pop-out menu like inventory tags or MES uses?
	}

	@Override
	public void shutDown() {
		//TODO: check if something needs to be added to startup or shutdown + profilechange / rebuild chatbox? => wat als je plugin togglet in raid, in tob lobby etc etc => varbits ook setten on startup zoals slayer plugin, niet enkel onVarbitChanged!
		//todo: potentially do some of this shit or stop filter when swapping profiles? check how other filter plugins do it
		//todo: find a way to disable filtering mode when disabling all the show friends/show cc etc options are disabled + to change the filter mode when config is changed + change text back in that case (&filter)
		//todo: function volgorde fixen, refactor
		//todo: final tests: login, hopping, toggling on/off plugin, toggling settings on/off, opening clan panels, changing to resizable, after npc chatbox maybe, in/after cutscenes like myths guild
		//todo: make code easier/clean-up by adding/changing methods, adding local variables etc
		//todo: remove chatstofilter if irrelevant
		//todo: remove TEST and remove println
		//todo: add comments
		//todo: fix potential interaction with smartchatinput recolor?

		shuttingDown = true; //Probably not necessary but just to be sure it doesn't fire
		if (client.getGameState() == GameState.LOGGED_IN || client.getGameState() == GameState.LOADING) {
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
			disableFilterWhenSetEmptied(); //todo: check order for this, should it be at the end or is it fine here?
			if (configChanged.getKey().equals("forcePrivateOn")) {
				if (forcePrivateOn) {
					//Set friends to on when forePrivateOn is enabled and chat is custom filtered
					setChatsToPublic(false);
				} else { //if (!forcePrivateOn)
					//Set friends status to non filtered and redraw chat buttons to show the current state of friends
					executeSetChatFilterConfig(ComponentID.CHATBOX_TAB_PRIVATE, false);
					redrawChatButtons(); //todo: does it have to set the text again after this?? maybe include it in redrawChatButtons() and then remove it somewhere else
				}
			}
		}
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged profileChanged) {
		setConfigFirstStart();
		shuttingDown = false;
		setChatStoneWidgetTextAll();
		setChatsToPublic(false);
	}

	private void updateConfig() {
		publicChatFilterOptions = config.publicChatFilterOptions();
		publicChatFilterOptions2D = config.publicChatFilterOptions2D();
		convertCommaSeparatedConfigStringToList(config.publicWhitelist(), publicWhitelist);
		privateChatFilterOptions = config.privateChatFilterOptions();
		convertCommaSeparatedConfigStringToList(config.privateWhitelist(), privateWhitelist);
		forcePrivateOn = config.forcePrivateOn();
		channelChatFilterOptions = config.channelChatFilterOptions();
		convertCommaSeparatedConfigStringToList(config.channelWhitelist(), channelWhitelist);
		clanChatFilterOptions = config.clanChatFilterOptions();
		convertCommaSeparatedConfigStringToList(config.clanWhitelist(), clanWhitelist);
		tradeChatFilterOptions = config.tradeChatFilterOptions();
		convertCommaSeparatedConfigStringToList(config.tradeWhitelist(), tradeWhitelist);

		showFriendsMessages = config.showFriendsMessages(); //todo: remove probs
		showCCMessages = config.showCCMessages();
		showFCMessages = config.showFCMessages();
		showGuestCCMessages = config.showGuestCCMessages();
		showRaidPartyMessages = config.showRaidPartyMessages();
		//The config values below are only set through ConfigManager and are not part of ChatFilterExtendedConfig.java
		//PS Probs don't try to refactor this; did not go well (on plugin start) the last time I tried that...
		publicFilterEnabled = configManager.getConfiguration("chat-filter-extended", "publicFilterEnabled", boolean.class);
		privateFilterEnabled = configManager.getConfiguration("chat-filter-extended", "privateFilterEnabled", boolean.class);
		channelFilterEnabled = configManager.getConfiguration("chat-filter-extended", "channelFilterEnabled", boolean.class);
		clanFilterEnabled = configManager.getConfiguration("chat-filter-extended", "clanFilterEnabled", boolean.class);
		tradeFilterEnabled = configManager.getConfiguration("chat-filter-extended", "tradeFilterEnabled", boolean.class);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
			setPublicChatOnInt = 2;
			setChatStoneWidgetTextAll();
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
	public void onCommandExecuted(CommandExecuted commandExecuted) { //todo: remove this
		if (commandExecuted.getCommand().equals("test")) {
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
		if (commandExecuted.getCommand().equals("test2")) {
			processToBPartyInterface();
		}
		if (commandExecuted.getCommand().equals("test3")) {
			executeSetChatFilterConfig(ComponentID.CHATBOX_TAB_PUBLIC,true);
			//todo: fix bug that randomly sets chatboxes back to public instead of custom when clicking on them
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

		//getActionParam1() seems to be getMenuEntry().getParam1() which seems to be getMenuEntry().getWidget().getId() = 10616843 = ComponentID (for public chat).
		//Only show MenuEntry when ShouldFilterChatType && one of the filter options is enabled
		int menuEntryAddedParam1 = menuEntryAdded.getActionParam1();
		//shouldFilterChatType already has a ComponentID check build in that checks if it's a chatstone or not.
		if (shouldFilterChatType(menuEntryAddedParam1) && menuEntryAdded.getOption().contains("Show none")) {
			//create MenuEntry and set its params
			final MenuEntry chatFilterEntry = client.createMenuEntry(-1).setType(MenuAction.RUNELITE_HIGH_PRIORITY);
			chatFilterEntry.setParam1(menuEntryAddedParam1).onClick(this::enableChatFilter);

			Set<ChatTabFilterOptions> set = componentIDToChatTabFilterSet(menuEntryAddedParam1);
			if (set != null) {
				//Set name of entry
				final StringBuilder optionBuilder = new StringBuilder();
				//Pull tab name from menu since Trade/Group is variable + set Option based on config settings
				String option = menuEntryAdded.getOption();
				int idx = option.indexOf(':');
				if (idx != -1) {
					optionBuilder.append(option, 0, idx).append(":</col> ");
				}
				optionBuilder.append("Show ");

				//Grab the abbreviations from the enum based on the selected config
				//Although maybe not the most optimal, I did not want to convert the HashSet to a List that I could order according to the enum at onConfigChanged
				//So I'm just looping over the enum values here
				for (ChatTabFilterOptions enumValue : ChatTabFilterOptions.values()) {
					if (set.contains(enumValue)) {
						optionBuilder.append(enumValue.toAbbreviationString()).append("/"); //alternatively just use the getter
					}
				}
				option = optionBuilder.toString();

				//Replace entries with their 2D equivalent if 2D is added to the 2D set
				//Order does not matter since I'm just replacing, so just iterate over the HashSet
				if (menuEntryAddedParam1 == ComponentID.CHATBOX_TAB_PUBLIC) {
					Set<ChatTabFilterOptions2D> set2D = componentIDToChatTabFilterSet2D(menuEntryAddedParam1);
					if (set2D != null) {
						for (ChatTabFilterOptions2D entry : set2D) {
							option = option.replace(entry.toNon2DAbbreviationString()+"/", entry.toAbbreviationString()+"/"); //A slash is added, so it does not result in: "Public 2D: Show Public 2D/Friends/CC 2D
						}
					}
				}
				option = option.substring(0, option.length() - 1); //Remove the trailing "/". If deleted earlier, the final option is not properly replaced by its 2D variant.
				chatFilterEntry.setOption(option);
			}
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked menuOptionClicked) {
		final int menuOptionClickedParam1 = menuOptionClicked.getParam1();
		final String menuOption = menuOptionClicked.getMenuOption();
		//The menu option for show friends is "<col=ffff00>Public:</col> Show friends"
		//First check if it's a valid chatfilter option, then check that it's not the one we inserted => disable chat filtering if user clicked e.g. Public: Show friends
		if (isComponentIDChatStone(menuOptionClickedParam1) && //alternatively you could use menuOption.contains("<col=ffff00>") && menuOption.contains("Show") instead of isComponentIDChatStone
				!menuOption.contains("Friends") && !menuOption.contains("CC") && !menuOption.contains("FC") && !menuOption.contains("Guest") && !menuOption.contains("Raid")) { //Plugin uses Friends instead of friends (osrs game)
			//todo: add the whitelist feature, Public, Public (2d) etc to that! Maybe also make it into a non editable list
			//If the specific chat is filtered, disable the filter. Technically the if statement could potentially be skipped.
			if (isChatFilteredComponentID(menuOptionClickedParam1)) {
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
			setChatStoneWidgetTextAll();
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

	private void convertCommaSeparatedConfigStringToList(String configString, List<String> listToConvertTo) {
		//Convert a CSV config string to a list
		listToConvertTo.clear();
		listToConvertTo.addAll(Text.fromCSV(Text.standardize(configString)));
	}

	@Nullable
	private Set<ChatTabFilterOptions> componentIDToChatTabFilterSet(int componentID) {
		//Returns the Set<ChatTabFilterOptions> based on the componentID. Originally had it in an Object with also 2D, but it's kind of annoying to use so screw that.
		//Returns null when componentID != chatstone componentID
		switch (componentID) {
			case ComponentID.CHATBOX_TAB_PUBLIC:
				return publicChatFilterOptions;
			case ComponentID.CHATBOX_TAB_PRIVATE:
				return privateChatFilterOptions;
			case ComponentID.CHATBOX_TAB_CHANNEL:
				return channelChatFilterOptions;
			case ComponentID.CHATBOX_TAB_CLAN:
				return clanChatFilterOptions;
			case ComponentID.CHATBOX_TAB_TRADE:
				return tradeChatFilterOptions;
		}
		return null;
	}

	@Nullable
	private Set<ChatTabFilterOptions2D> componentIDToChatTabFilterSet2D(int componentID) { //TODO: remove if redundant
		//Returns the Set<ChatTabFilterOptions2D> based on the componentID. Originally had it in an Object with also 3D/regular, but it's kind of annoying to use so screw that.
		//Returns null when componentID != public chatstone componentID
		if (componentID == ComponentID.CHATBOX_TAB_PUBLIC) {
			return publicChatFilterOptions2D;
		}
		return null;
	}

	private boolean shouldFilterChatType(int componentID) {
		//Should a chat stone (e.g. private) be filtered based on the componentID and the config set
		Set<ChatTabFilterOptions> set = componentIDToChatTabFilterSet(componentID);
		//componentIDToChatTabFilterSet already checks the componentID, so we don't have to check if it's a chat stone componentID besides doing a null check
		//The public2D filter only works when the normal one is also active, so can ignore the 2D one for now.
		if (set != null) {
			return !set.isEmpty();
		}
		return false;
	}

	private void redrawChatButtons() {
		if (client.getGameState() == GameState.LOGGED_IN || client.getGameState() == GameState.LOADING) {
			clientThread.invokeLater(() -> {
				client.runScript(REDRAW_CHAT_BUTTONS_SCRIPTID); //[proc,redraw_chat_buttons]
			});
		}
	}

	private void disableFilterWhenSetEmptied() {
		//Disable currently active filter + rebuild chatbuttons if all filters for a chat tab get disabled in config
		//Called in onConfigChanged
		boolean shouldRedraw = false;
		//Update arrays to most recent values
		boolean[] filtersEnabled = new boolean[]{publicFilterEnabled, privateFilterEnabled, channelFilterEnabled, clanFilterEnabled, tradeFilterEnabled};
		//Iterate through all chat filter enabled booleans and check if they should be active according to the config or not
		for (int i = 0; i < filtersEnabled.length; i++) {
			if (filtersEnabled[i] && !shouldFilterChatType(chatboxComponentIDs.get(i))) {
				executeSetChatFilterConfig(chatboxComponentIDs.get(i), false);
				shouldRedraw = true;
			}
		}
		//If a chat filter has been disabled because the config set has been emptied, redraw all chat buttons, then set the Custom text again for all active ones
		if (shouldRedraw) {
			redrawChatButtons();
			setChatStoneWidgetTextAll();
		}
	}

	private void setConfigFirstStart() {
		//todo: if changing config stuff that's not in ChatFilterExtendedConfig, change this as well
		//Config keys that are not part of ChatFilterExtendedConfig are still empty on first startup. Prevent them being null by setting them before other code checks the config keys.
		for (String filtersEnabledString : filtersEnabledStringList) {
			if (configManager.getConfiguration("chat-filter-extended", filtersEnabledString) == null) {
				configManager.setConfiguration("chat-filter-extended", filtersEnabledString, false);
			}
		}
	}

	private boolean isComponentIDChatStone(int componentID) {
		return chatboxComponentIDs.contains(componentID);
	}

	private void enableChatFilter(MenuEntry menuEntry) {
		setChatFilterConfig(menuEntry, true);
		setChatsToPublic(false);
		setChatStoneWidgetTextAll();
	}

	private void setChatFilterConfig(MenuEntry menuEntry, boolean enableFilter) {
		int menuComponentID = menuEntry.getParam1();
		if (isComponentIDChatStone(menuComponentID)) {
			executeSetChatFilterConfig(menuComponentID, enableFilter);
		}
	}

	private void executeSetChatFilterConfig(int componentID, boolean enableFilter) {
		//Separate function, so it can be easily run by putting in the componentID instead having to enter a MenuEntry
		//publicFilterEnabled = enableFilter is not necessary since ConfigManager does trigger updateConfig() if the config value actually gets changed from false to true or vice versa
		//Alternatively use a switch(componentID) statement like you did before. It's probably more efficient execution wise, but we got these lists anyway and this is more compact
		for (int i = 0; i < chatboxComponentIDs.size(); i++) {
			if (chatboxComponentIDs.get(i) == componentID) {
				configManager.setConfiguration("chat-filter-extended", filtersEnabledStringList.get(i), enableFilter);
			}
		}
	}

	private void setChatsToPublic(boolean onlyVolatile) {
		//Set chat tabs to public, so they can then be filtered by the plugin
		if (client.getGameState() == GameState.LOGGED_IN || client.getGameState() == GameState.LOADING) {
			//Public, private, trade remember between hops => they are only set when onlyVolatile is set to false.
			//Channel and clan are probs varbit based => they are always set when this method is executed ("volatile").
			//Channel, Clan don't remember between hopping; potentially related to varbits as described here https://discord.com/channels/301497432909414422/301497432909414422/1086022946633547867 (i.e. I suspect when hopping it reads the state from the varbits and then sets the chat filters according to those values)
			clientThread.invokeLater(() -> {
				if (!onlyVolatile) {
					//todo: explain the volatile thing better + change script ids and maybe arguments to static final ints?
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

	private void setChatStoneWidgetTextAll() {
		//Sets the WidgetText for enabled chats to Custom
		if (client.getGameState() != GameState.LOGGED_IN && client.getGameState() != GameState.LOADING) {
			return;
		}
		for (int componentID : chatboxComponentIDs) {
			setChatStoneWidgetText(componentID);
		}
	}

	private void setChatStoneWidgetText(int componentID) {
		//Sets the WidgetText for the specific chat to Custom, based on componentID. Usage of this already has GameState check.
		Widget chatWidget = client.getWidget(componentID);
		if (chatWidget != null && isChatFilteredComponentID(componentID)) {
			setCustomText(chatWidget);
		}
	}

	private void setCustomText(Widget widget) {
		final String customTextString = "<br><col=ffff00>Custom</col>";
		widget.getStaticChildren()[2].setText(customTextString); //or e.g. chatWidget.getStaticChildren().length-1
	}

	private boolean isChatFilteredComponentID(int componentID) {
		switch (componentID) {
			case ComponentID.CHATBOX_TAB_PUBLIC:
				return publicFilterEnabled;
			case ComponentID.CHATBOX_TAB_PRIVATE:
				return privateFilterEnabled;
			case ComponentID.CHATBOX_TAB_CHANNEL:
				return channelFilterEnabled;
			case ComponentID.CHATBOX_TAB_CLAN:
				return clanFilterEnabled;
			case ComponentID.CHATBOX_TAB_TRADE:
				return tradeFilterEnabled;
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

	private boolean shouldFilterMessagePlayerName(String playerName) { //todo: rework this to be specific per chatMessageType or ComponentID
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
		HashSet<String> raidPartyStandardizedUsernamesTemp = new HashSet<>();
		if (topHalfToBBoardWidget != null && topHalfToBBoardWidget.getChildren() != null) {
			for (int i = 0; i < topHalfToBBoardWidget.getChildren().length; i++) {
				//Get child that has type 3 => next one has to be name
				if (topHalfToBBoardWidget.getChild(i).getType() == 3) {
					//Index of the one that has name is type 3 index + 1
					int indexName = topHalfToBBoardWidget.getChild(i).getIndex() +1;
					if (topHalfToBBoardWidget.getChild(indexName).getType() == 4) {
						//If right type (4), get the text and standardize it
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
		Widget tobPartyInterfaceNamesWidget = client.getWidget(InterfaceID.TOB, TOB_PARTY_INTERFACE_NAMES_CHILDID); //S28.12
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