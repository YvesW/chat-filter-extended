package com.ywcode.chatfilterextended;

import com.google.common.base.*;
import com.google.common.collect.*;
import com.google.inject.*;
import lombok.extern.slf4j.*;
import net.runelite.api.*;
import net.runelite.api.clan.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.*;
import net.runelite.client.config.*;
import net.runelite.client.eventbus.*;
import net.runelite.client.events.*;
import net.runelite.client.party.*;
import net.runelite.client.party.events.*;
import net.runelite.client.plugins.*;
import net.runelite.client.util.*;

import javax.annotation.*;
import javax.inject.Inject;
import java.util.*;

@Slf4j
@PluginDescriptor(
		name = "Chat Filter Extended",
		description = "Extends the functionality of the chat tabs/stones to filter chat messages not from friends/clan members/FC members/Guest CC members/raid members/party members.",
		tags = {"chat,chat filter,public,public 2d,friends,friends 2d,fc,fc 2d,cc,cc 2d,guest,guest 2d,raid,raid 2d,party,party 2d,whitelist,whitelist 2d,custom,clanchat,clan,filter,friends chat,private,trade,raids,tob,toa,cox,spam,show"}
)
//Alternative (shitty) names: Custom Chat View, Chat View Extended, Chat Show Custom, Custom Chat Filter, Chat tabs extended, Chat stones extended
//My goal was not to make one of these "abc improved" or "better abc" plugins, but the menuOptions like "Show friends" or "Show none" are just called chat filters, I think, and I can't come up with a better name. At least polar calls them that in e.g. script 152 (chat_set_filter)
//It's just unlucky that the chat filter plugin (which is a perfectly valid name for its function) is also called chat filter, I guess.

public class ChatFilterExtendedPlugin extends Plugin {

	// ------------- Wall of config vars -------------
	// Vars are quite heavily cached so could probably just config.configKey(). However, the best practice behavior in plugins is to have a bunch of variables to store the results of the config methods, and check it in startUp/onConfigChanged. It feels redundant, but it's better than hitting the reflective calls every frame. --LlemonDuck. Additionally, the whitelist strings are actually getting processed.
	private static Set<ChatTabFilterOptions> publicChatFilterOptions = new HashSet<>();
	private static Set<ChatTabFilterOptions2D> publicChatFilterOptions2D = new HashSet<>();
	private static final HashSet<String> publicWhitelist = new HashSet<>();
	private static Set<ChatTabFilterOptions> privateChatFilterOptions = new HashSet<>();
	private static final HashSet<String> privateWhitelist = new HashSet<>();
	private static boolean forcePrivateOn;
	private static Set<ChatTabFilterOptions> channelChatFilterOptions = new HashSet<>();
	private static final HashSet<String> channelWhitelist = new HashSet<>();
	private static Set<ChatTabFilterOptions> clanChatFilterOptions = new HashSet<>();
	private static final HashSet<String> clanWhitelist = new HashSet<>();
	private static Set<ChatTabFilterOptions> tradeChatFilterOptions = new HashSet<>();
	private static final HashSet<String> tradeWhitelist = new HashSet<>();
	private static boolean clearChannelSetHop;
	private static boolean clearClanSetHop;
	private static boolean clearGuestClanSetHop;
	private static boolean clearRaidPartySetHop;
	private static boolean clearRLPartySetHop;
	private static boolean clearChannelSetLeave;
	private static boolean clearClanSetLeave;
	private static boolean clearGuestClanSetLeave;

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
	private static final String configGroup = "ChatFilterExtended";
	private static boolean shuttingDown; //Default value is false
	private static boolean setChatsToPublicFlag; //Default value is false
	private static final HashSet<String> channelStandardizedUsernames = new HashSet<>();
	private static final HashSet<String> clanStandardizedUsernames = new HashSet<>();
	private static final HashSet<String> guestClanStandardizedUsernames = new HashSet<>();
	private static final HashSet<String> raidPartyStandardizedUsernames = new HashSet<>();
	private static final HashSet<String> runelitePartyStandardizedUsernames = new HashSet<>(); //TODO: add code to handle this
	private static final List<Integer> chatboxComponentIDs = ImmutableList.of(ComponentID.CHATBOX_TAB_PUBLIC, ComponentID.CHATBOX_TAB_PRIVATE, ComponentID.CHATBOX_TAB_CHANNEL, ComponentID.CHATBOX_TAB_CLAN, ComponentID.CHATBOX_TAB_TRADE);
	private static final List<String> filtersEnabledStringList = ImmutableList.of("publicFilterEnabled", "privateFilterEnabled", "channelFilterEnabled", "clanFilterEnabled", "tradeFilterEnabled");
	private static boolean inCoXRaidOrLobby; //Default value is false
	private static int getRLPartyMembersFlag; //Default is 0
	private static final int TOA_LOBBY_REGION_ID = 13454;
	private static final int COX_BANK_REGION_ID = 4919;
	private static final int TOA_IN_RAID_VARPID = 2926; //Changes to e.g. 1001 when entering ToA, then when leaving it does: 1001 -> 1000 -> 1200 -> 0.
	private static final int REDRAW_CHAT_BUTTONS_SCRIPTID = 178; //[proc,redraw_chat_buttons]
	private static final int CHAT_SET_FILTER_SCRIPTID = 152; //[clientscript,chat_set_filter]
	private static final int TOB_PARTYDETAILS_BACK_BUTTON_SCRIPTID = 4495; //[proc,tob_partydetails_back_button]
	private static final int TOA_PARTYDETAILS_BACK_BUTTON_SCRIPTID = 6765; //[proc,toa_partydetails_back_button]
	private static final int TOB_BOARD_ID = 50; //N50.0
	private static final int TOP_HALF_TOB_BOARD_CHILDID = 27; //S50.27
	private static final int BOTTOM_HALF_TOB_BOARD_CHILDID = 42; //S50.42
	private static final int TOB_HUD_DRAW_SCRIPTID = 2297; //proc,tob_hud_draw Does proc every tick outside though and also (less frequently) in the raid.
	private static final int TOB_PARTY_INTERFACE_NAMES_CHILDID = 12; //S28.12
	private static String previousRaidPartyInterfaceText; //null by default
	private static final int TOA_BOARD_ID = 774; //S774.32
	private static final int MEMBERS_TOA_BOARD_CHILDID = 32; //S774.32
	private static final int APPLICANTS_TOA_BOARD_CHILDID = 48; //S774.48
	private static final int TOA_PARTY_INTERFACE_NAMES_CHILDID = 5; //S773.5
	private static final int FC_CHAT_FILTER_VARBIT = 928; //These get read in e.g. [proc,chat_get_filter] (185)
	private static final int CC_CHAT_FILTER_VARBIT = 929; //These get read in e.g. [proc,chat_get_filter] (185)
	private static final int TOB_IN_RAID_VARCSTR_PLAYER1_INDEX = 330; //330-334 is player 1-5's name when IN the raid (does not work when applying/accepting on the notice board or in the lobby). Returns an empty string if there is not e.g. a player 5. Probably updates each room.
	private static final int TOB_IN_RAID_VARCSTR_PLAYER5_INDEX = 334;
	private static final int TOA_IN_RAID_VARCSTR_PLAYER1_INDEX = 1099; //1099-1106 is player 1-8's name when IN the raid (does not work when applying/accepting on the obelisk or in the lobby). Returns an empty string if there is not e.g. a player 8. Probably updates each room.
	private static final int TOA_IN_RAID_VARCSTR_PLAYER8_INDEX = 1106;
	private static final HashSet<Long> partyMemberIds = new HashSet<>();
	private static int getRLPartyUserJoinedMembersFlag; //Default is 0
	//Collection cheat sheet: https://i.stack.imgur.com/POTek.gif (that I did not fully adhere to lol)
	//PM Can probably replace part of the methods by incorporating the info in the Enum and then using a getter, but enfin

	@Inject
	private Client client;

	@Inject
	private ChatFilterExtendedConfig config;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private PartyService partyService;

	@Override
	public void startUp() {
		shuttingDown = false; //Maybe it got procced by switching profiles, assuming plugins are all shutdown and started again?
		setConfigFirstStart(); //todo: check if this needs more config vars added to it after config changes
		updateConfig();
		setChatsToPublic();
		addAllInRaidUsernamesVarClientStr(); //Will also add a raid group to the hashset if you are not inside ToB/ToA anymore. This is fine and can be useful in certain situations, e.g. getting a scythe, teleporting to the GE to get the split and then turning on the plugin at the GE. You can still see your raid buddies' messages then. If this is undesired, replace with getToBPlayers() and getToAPlayers()
		setAddPartyMemberStandardizedUsernamesFlag(); //In case the plugin is started while already in a party.
		clientThread.invokeLater(this::getCoXVarbit); //Get varbit in case the plugin is started while logged in.
		clientThread.invokeLater(this::getCoXPlayers); //Get CoX players because it does not trigger onPlayerSpawned while inside a raid if the players have already spawned before the plugin is turned on.
		clientThread.invokeLater(this::processToBBoard); //User might technically enable plugin and exit the ToB board before the refresh scriptid procs.
		clientThread.invokeLater(this::processToABoard); //User might technically enable plugin and exit the ToA board before the refresh scriptid procs.

		//todo: add readme
		//todo: go through problems
		//todo: Change config thing to have different filters per chat so one for public, one for private + add config setting to add only 2d text for some people but not into chatbox? So then it'd only hide the chatbox stuff from those people => only for public chat cause rest is chatbox only... Including randos? So you could e.g. be everyone 2d except friends also chatbox but clan fully filtered? Requires public to also be added to the initial options!
		//todo: make whitelist toCSV. Keep in mind that you also add that to the options, but then also ctrl+F it, because you got some code that looks at it to determine if chat is filtered!
		//todo: check and refactor the whole fucking shit + check if you can replace widget crawling with varcstrings etc
		//todo: mss per region settings in advanced doch mss gaat dit te ver + add chat message mss wanneer je iets op custom zet (doch mss wat te spammy) maar def als je private verandert dat het on is! (En mss wrm het niet werkt als je die setting niet enabled hebt?) => echter probs te spammy wat support traffic geeft, dus probs skippen want de config setting is heel duidelijk
		//todo: potentially add player right-click option (if a config option is enabled, maybe also option to make this only when shift is held down?) to add to a whitelist with a pop-out menu like inventory tags or MES uses?
		//todo: get varbits here somewhere when logged in probs, if it uses varbits (or e.g. add to list when tobbing/in tob region etc)
		//todo: maybeee make when to clear fc/cc/raid etc a selectable option under an advanced config tab
		//todo: denk aan chat highlight gedoe ignoren als filtered dat niet een rando het proct terwijl zijn chat eigenlijk filtered is
		//todo: mss optie om in bepaalde regionids automatisch op custom te zetten mr idk
		//todo: denk over alle hashsets na en wanneer je bijv. al in een raid party bent, of wanneer je al in een clan bent / wanneer er al guests in je cc zijn etc. Get all that info here like addAllInRaidUsernamesVarClientStr()
		//todo: alles wat niet ongametick of op een script draait dat ELKE GAMETICK PROCT onstartup doen (denk ook aan clan members, fc members hashset etc etc! die vallen buiten raids en moet je dus nog doen!) en tevens wanneer je raidsparty cleared via bijv rightclick menu of bijv via van tob nr cox tpen DUS OOK DE VARC SHIT PROBS ALS DAT NULL IS BUITEN TOB EN TOA!!
		//TODO: at some point consider adding a pop-out menu like MES has to Show custom in which you can add or remove options from the set.
		//TODO: at some point consider adding a pop-out menu like MES has to other players to add/remove them from the whitelist. Potentially only added when shift is held.
	}

	@Override
	public void shutDown() {
		//TODO: check if something needs to be added to startup or shutdown + profilechange / rebuild chatbox? => wat als je plugin togglet in raid, in tob lobby etc etc => varbits ook setten on startup zoals slayer plugin, niet enkel onVarbitChanged!
		//todo: potentially do some of this shit or stop filter when swapping profiles? check how other filter plugins do it
		//todo: find a way to disable filtering mode when disabling all the show friends/show cc etc options are disabled + to change the filter mode when config is changed + change text back in that case (&filter)
		//todo: method volgorde fixen, refactor
		//todo: final tests: login, hopping, toggling on/off plugin, toggling settings on/off, opening clan panels, changing to resizable, after npc chatbox maybe, in/after cutscenes like myths guild
		//todo: make code easier/clean-up by adding/changing methods, adding local variables etc
		//todo: remove TEST and remove println
		//todo: add comments
		//todo: fix potential interaction with smartchatinput recolor? removing client.getGameState() == GameState.LOADING things does not fix it...
		//todo: test a bit what happens when putting clan and e.g. public to off, then enabling custom, then disabling the plugin => should go back to off for both? probably? and what if you then reboot the client?
		//todo: check if cox widgetids, scriptids al ergens in runelite bestaan of niet


		shuttingDown = true; //Might not be necessary but just to be sure it doesn't set it back to custom text since the script procs
		if (client.getGameState() == GameState.LOGGED_IN || client.getGameState() == GameState.LOADING) {
			clientThread.invoke(() -> {
				//This rebuilds both the chatbox and the pmbox
				client.runScript(ScriptID.SPLITPM_CHANGED);
				client.runScript(REDRAW_CHAT_BUTTONS_SCRIPTID); //[proc,redraw_chat_buttons]
			});
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged) {
		if (configChanged.getGroup().equals(configGroup)) {
			updateConfig();
			disableFilterWhenSetEmptied();
			if (configChanged.getKey().equals("forcePrivateOn")) {
				if (forcePrivateOn) {
					//Set friends to on when forePrivateOn is enabled and chat is custom filtered
					setChatsToPublic();
				} else { //if (!forcePrivateOn)
					//Set friends status to non filtered and redraw chat buttons to show the current state of friends -> also sets the other chats to custom again if needed because redrawChatButtons procs a script that triggers the code setting the text of the chat buttons.
					executeSetChatFilterConfig(ComponentID.CHATBOX_TAB_PRIVATE, false);
					redrawChatButtons();
				}
			}
		}
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged profileChanged) {
		setConfigFirstStart();
		shuttingDown = false;
		setChatsToPublic();
		redrawChatButtons();
	}

	private void updateConfig() {
		publicChatFilterOptions = config.publicChatFilterOptions();
		publicChatFilterOptions2D = config.publicChatFilterOptions2D();
		convertCommaSeparatedConfigStringToSet(config.publicWhitelist(), publicWhitelist);
		privateChatFilterOptions = config.privateChatFilterOptions();
		convertCommaSeparatedConfigStringToSet(config.privateWhitelist(), privateWhitelist);
		forcePrivateOn = config.forcePrivateOn();
		channelChatFilterOptions = config.channelChatFilterOptions();
		convertCommaSeparatedConfigStringToSet(config.channelWhitelist(), channelWhitelist);
		clanChatFilterOptions = config.clanChatFilterOptions();
		convertCommaSeparatedConfigStringToSet(config.clanWhitelist(), clanWhitelist);
		tradeChatFilterOptions = config.tradeChatFilterOptions();
		convertCommaSeparatedConfigStringToSet(config.tradeWhitelist(), tradeWhitelist);
		clearChannelSetHop = config.clearChannelSetHop();
		clearClanSetHop = config.clearClanSetHop();
		clearGuestClanSetHop = config.clearGuestClanSetHop();
		clearRaidPartySetHop = config.clearRaidPartySetHop();
		clearRLPartySetHop = config.clearRLPartySetHop();
		clearChannelSetLeave = config.clearChannelSetLeave();
		clearClanSetLeave = config.clearClanSetLeave();
		clearGuestClanSetLeave = config.clearGuestClanSetLeave();

		showFriendsMessages = config.showFriendsMessages(); //todo: remove when you removed it from all the code + remove it from config and at the top of the file then
		showCCMessages = config.showCCMessages();
		showFCMessages = config.showFCMessages();
		showGuestCCMessages = config.showGuestCCMessages();
		showRaidPartyMessages = config.showRaidPartyMessages();
		//The config values below are only set through ConfigManager and are not part of ChatFilterExtendedConfig.java
		//PS Probs don't try to refactor this; did not go well (on plugin start) the last time I tried that...
		publicFilterEnabled = configManager.getConfiguration(configGroup, "publicFilterEnabled", boolean.class);
		privateFilterEnabled = configManager.getConfiguration(configGroup, "privateFilterEnabled", boolean.class);
		channelFilterEnabled = configManager.getConfiguration(configGroup, "channelFilterEnabled", boolean.class);
		clanFilterEnabled = configManager.getConfiguration(configGroup, "clanFilterEnabled", boolean.class);
		tradeFilterEnabled = configManager.getConfiguration(configGroup, "tradeFilterEnabled", boolean.class);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		switch (gameStateChanged.getGameState()) {
			case LOGGED_IN:
				setChatsToPublic();
				setAddPartyMemberStandardizedUsernamesFlag(); //Will also proc after loading gamestate, but this will ensure that the party hashset is also correctly populated after fully logging out but remaining in the party.
				break;
			case LOGIN_SCREEN:
				clanStandardizedUsernames.clear();
				channelStandardizedUsernames.clear();
				guestClanStandardizedUsernames.clear();
				clearRaidPartyHashset(); //Also clear the string so the plugin will process the party interface if needed
				runelitePartyStandardizedUsernames.clear();
				break;
			case HOPPING:
				//Clear raid & RL party members while hopping because you generally don't care about them anymore after hopping to another world
				if (clearRaidPartySetHop) {
					clearRaidPartyHashset(); //Also clear the string so the plugin will process the party interface if needed
				}
				if (clearRLPartySetHop) {
					runelitePartyStandardizedUsernames.clear();
				}
				if (clearChannelSetHop) {
					channelStandardizedUsernames.clear();
				}
				if (clearClanSetHop) {
					clanStandardizedUsernames.clear();
				}
				if (clearGuestClanSetHop) {
					guestClanStandardizedUsernames.clear();
				}
				//todo: test if things like cc, channel, guestclan clear on hop would be problematic. Otherwise probs remove.
				break;
		}
	}

	@Subscribe
	public void onFriendsChatChanged(FriendsChatChanged friendsChatChanged) {
		//Remove FC usernames when leaving the FC and when the advanced config option is enabled; also procs when hopping/logging out
		if (!friendsChatChanged.isJoined() && clearChannelSetLeave) {
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
		//If left CC => clear own cc usernames HashSet //todo: check if you want to do this instead of the private boolean isClanChatMember(String playerName) { thing because it's still a cc member, even if you leave the chat, right? => Clan thing: use both approaches so it also accounts for guests in the cc & works when pressing leave probs
		if (!clanChannelChanged.isGuest()) { //If left or joined own CC or GIM chat
			int clanId = clanChannelChanged.getClanId();
			if (clanId == ClanID.CLAN) { //If left/joined own CC, separate line because of all the if-then-else statements used here
				if (client.getClanChannel(ClanID.CLAN) == null) { //If not in own CC
					if (clearClanSetLeave) {
						clanStandardizedUsernames.clear();
					}
				} else { //If in own CC
					//If joined own CC, get members and add the usernames to HashSet
					addClanMembers(clanChannelChanged, client.getClanSettings(), clanStandardizedUsernames);
				}
			}

			//Also include GIM members in Clan Hashset, untested because no access to a GIM account
			if (clanId == ClanID.GROUP_IRONMAN) { //If joined/left GIM chat
				if (client.getClanChannel(ClanID.GROUP_IRONMAN) != null && clanChannelChanged.getClanChannel() != null) { //If in GIM chat
					List<ClanChannelMember> gimMembers = clanChannelChanged.getClanChannel().getMembers();
					for (ClanChannelMember gimMember : gimMembers) {
						clanStandardizedUsernames.add(Text.standardize(gimMember.getName()));
					}
				}
			}

		} else { //If left/joined guest CC
			if (client.getGuestClanChannel() == null) { //If not in guest CC
				if (clearGuestClanSetLeave) { //If left guest CC => clear guest cc usernames HashSet if the advanced config option is enabled
					guestClanStandardizedUsernames.clear();
				}
			} else { //If joined guest clan
				//If joined guest clan, get members and add the usernames to HashSet
				addClanMembers(clanChannelChanged, client.getGuestClanSettings(), guestClanStandardizedUsernames);
			}
		}
	}

	@Subscribe
	public void onClanMemberJoined(ClanMemberJoined clanMemberJoined) {
		//getClanSettings.GetMembers won't include any guests; add newly joined guests this way
		//In the case of HashSet, the item isn't inserted if it's a duplicate => so no .contains check beforehand.
		String standardizedJoinedName = Text.standardize(clanMemberJoined.getClanMember().getName());
		ClanChannel clanChannel = client.getClanChannel();
		ClanChannel guestClanChannel = client.getGuestClanChannel();

		//If username of joined clanmember is in the cc, add to HashSet
		if (clanChannel != null && clanChannel.findMember(standardizedJoinedName) != null) { //findMember works both with .removeTags and with .standardize
			clanStandardizedUsernames.add(standardizedJoinedName);
		}

		//If username of joined clanmember is in the guest cc, add to HashSet
		if (guestClanChannel != null && guestClanChannel.findMember(standardizedJoinedName) != null) {
			guestClanStandardizedUsernames.add(standardizedJoinedName);
		}
	}

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
						System.out.println("running client.runScript(CHAT_SET_FILTER_SCRIPTID," + test + "," + test2 + ")");
						client.setVarbit(928, 0);
						client.runScript(CHAT_SET_FILTER_SCRIPTID, Integer.parseInt(test), Integer.parseInt(test2));
						client.setVarbit(928, 0);
						//client.runScript(CHAT_SET_FILTER_SCRIPTID, 2, 0); public
						//3, 0 for private
						//4, 0 for channel
						//5, 0 for clan
						//6, 0 for trade
					}
				}
			});
		}
		if (commandExecuted.getCommand().equals("test2")) {
			System.out.println(clanStandardizedUsernames);
			System.out.println(guestClanStandardizedUsernames);
		}
		if (commandExecuted.getCommand().equals("test3")) {
			System.out.println(publicFilterEnabled);
			System.out.println(configManager.getConfiguration(configGroup, "publicFilterEnabled"));
		}
		if (commandExecuted.getCommand().equals("test4")) {
			System.out.println(runelitePartyStandardizedUsernames);
			//raidPartyStandardizedUsernames.clear();
		}
		if (commandExecuted.getCommand().equals("test5")) {
			clearRaidPartyHashset();
		}
	}

	@Subscribe(priority = -4) //Run after ChatMessageManager, core ChatFilterPlugin (which is -2) etc
	public void onChatMessage(ChatMessage chatMessage) {
		//todo: add chat filter!
	}

	@Subscribe(priority = -2) //Run after chatfilter plugin etc, probably not necessary but can't hurt
	public void onOverheadTextChanged(OverheadTextChanged overheadTextChanged) {
		//todo: add chat filter when public filter is enabled! (but 2d is disabled!)
	}

	@Subscribe(priority = -2) //Run after ChatHistory plugin etc, probably not necessary but can't hurt
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
				Set<ChatTabFilterOptions2D> set2D = componentIDToChatTabFilterSet2D(menuEntryAddedParam1); //Already checks if componentID = public chat
				if (set2D != null) {
					for (ChatTabFilterOptions2D entry : set2D) {
						option = option.replace(entry.toNon2DAbbreviationString() + "/", entry.toAbbreviationString() + "/"); //A slash is added, so it does not result in: "Public 2D: Show Public 2D/Friends/CC 2D
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
		String menuOption = menuOptionClicked.getMenuOption();
		//The menu option for show friends is "<col=ffff00>Public:</col> Show friends"
		//First check if it's a valid chatfilter option, then check that it's not the one we inserted => disable chat filtering if user clicked e.g. Public: Show friends
		//Since this also contains stuff like "Switch tab", "Clear history" etc which should not turn off the custom filter, we also check for "Show" and if it does not contain some text from a menu entry we added ourselves.
		if (isComponentIDChatStone(menuOptionClickedParam1) && menuOption.contains("Show")) { //alternatively you could use menuOption.contains("<col=ffff00>") && menuOption.contains("Show") ?
			//Remove everything before the : so it doesn't match <col=ffff00>Public:</col>
			int idx = menuOption.indexOf(':');
			if (idx != -1) {
				menuOption = menuOption.substring(idx);
			}
			for (ChatTabFilterOptions enumValue : ChatTabFilterOptions.values()) { //All the abbreviations from ChatTabFilterOptions2D contain the non-2D abbreviation so contains still matches
				if (menuOption.contains(enumValue.toAbbreviationString())) { //Plugin uses Friends instead of friends (osrs game)
					//return in case it is the menu entry/option we added ourselves! We do not want to turn off the filter when clicking on our menu entry/option.
					return;
				}
			}
			//If the specific chat is filtered, disable the filter. Technically the if statement could potentially be skipped.
			if (isChatFilteredComponentID(menuOptionClickedParam1)) {
				setChatFilterConfig(menuOptionClicked.getMenuEntry(), false);
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick) {
		if (setChatsToPublicFlag) {
			executeSetChatsToPublic();
			setChatStoneWidgetTextAll(); //Also executed in setChatsToPublic() to improve the feeling (makes it feel snappier)
			setChatsToPublicFlag = false;
		}

		int regionId = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID();
		switch (regionId) {
			case TOA_LOBBY_REGION_ID:
				//Couldn't find any ScriptID/Varbit/Varp/VarCString that updated when the text from the toa party interface updates (besides script 6612 and 6613 / varbit 14345 changing the No party/Party/Step inside now! header when first joining a party). Let's check if the widget is not null and not hidden every game tick when inside the toa lobby aka region id 13454.
				processToAPartyInterface();
				break;
			case COX_BANK_REGION_ID:
				//There is no interface/widget/varc I could find. Also, nothing I could find that runs when a user gets added to the party here.
				getCoXBankPlayers();
				break;
		}

		if (getRLPartyMembersFlag > 0) { //Flag so party getMembers is not empty
			getRLPartyMembersFlag--; //Method can set int to 0 so -- first
			addPartyMemberStandardizedUsernames();
		}

		if (getRLPartyUserJoinedMembersFlag > 0) { //Flag because memberJoined displayname is not immediately available.
			getRLPartyUserJoinedMembersFlag--; //Method can set int to 0 so -- first
			addUserJoinedPartyStandardizedUsernames();
		}
	}

	//todo: re-enable chat history plugin after testing
	//todo: remove system.out.println & //test

	@Subscribe
	public void onScriptPostFired(ScriptPostFired scriptPostFired) {
		switch (scriptPostFired.getScriptId()) {
			case REDRAW_CHAT_BUTTONS_SCRIPTID:
				//178 = [proc,redraw_chat_buttons]
				if (!shuttingDown) {
					setChatStoneWidgetTextAll();
				}
				break;
			case TOB_PARTYDETAILS_BACK_BUTTON_SCRIPTID:
				//[proc,tob_partydetails_back_button].cs2 seems to trigger when opening party and when applying at the end, after 2317 has procced multiple times to add all the info (2317 proccs once per potential team member to add them to the board interface iirc)
				processToBBoard();
				break;
			case TOB_HUD_DRAW_SCRIPTID:
				//Procs once per tick, also procs inside of ToB. However, then S28.5 TOB_PARTY_INTERFACE and S28.12 (client.getWidget(InterfaceID.TOB, TOB_PARTY_INTERFACE_NAMES_CHILDID)) are hidden
				processToBPartyInterface();
				break;
			case TOA_PARTYDETAILS_BACK_BUTTON_SCRIPTID:
				//First 6615 [clientscript,toa_partydetails_init] is ran to probably initialize it. Then 6722 [clientscript,toa_partydetails_addmember] runs 8 times to add one member each. Then 6761 [proc,toa_partydetails_sortbutton_draw] runs 10 times. Finally, 6765 [proc,toa_partydetails_back_button], 6756 [proc,toa_partydetails_summary], and 6770 [proc,toa_invocations_side_panel_update] proc once. All in the same gamecycle.
				processToABoard();
				break;
		}
	}
	//TODO: set int or something to 1 when in/at tob, 2 toa, 3 cox (worldpoint for at probs and then varbits for in raid? check cox plugin, tob plugins, toa plugin for varbits. check discord plugin for wordlpoints/regions (although banks are probs missing))
	//todo: reset tob raid list e.g. when entering cox/toa zone, when hopping (already does iirc), on logout (already does iirc), other conditions? clear on disband (probs not though, geeft evt chat message but idk)
	//TODO: test what happens when applying and accepted, test what happens when someone else applies and you accept them, test what happens when someone applies and someone else accepts them and you have screen open/closed, test what happens when someone applies and you are not party leader but open board after applying (not accepted yet), test what happens when someone applies and you are not party leader but have board open while he applies (not accepted yet), test what happens in raid?

	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged) {
		//For some reason the channel and clan tab also have Varbits attached to them, while public, private, and trade do not.
		int varbitId = varbitChanged.getVarbitId();
		if ((channelFilterEnabled && varbitId == FC_CHAT_FILTER_VARBIT && varbitChanged.getValue() != 0) || //928 = FC value, 929 = cc value (int $chatfilter1). 0 = show all IIRC.
				(clanFilterEnabled && varbitId == CC_CHAT_FILTER_VARBIT && varbitChanged.getValue() != 0)) {
			setChatsToPublic();
		}
		if (varbitId == Varbits.IN_RAID) {
			inCoXRaidOrLobby = varbitChanged.getValue() > 0; //Convert the int to boolean. 0 = false, 1 = true.
			if (inCoXRaidOrLobby) {
				getCoXPlayers(); //playerSpawned procs before the varbit is set when joining a CoX lobby, so gotta also run this once when the varbit changes.
			}
		}
	}

	@Subscribe
	public void onVarClientStrChanged(VarClientStrChanged varClientStrChanged) {
		addInRaidUsernamesVarClientStr(varClientStrChanged.getIndex()); //Already tests if this is the correct index or not
	}

	@Subscribe(priority = -2) //Run after any other core party code (PartyPlugin)
	public void onPartyChanged(PartyChanged partyChanged) {
		setAddPartyMemberStandardizedUsernamesFlag();
	}

	@Subscribe(priority = -2) //Run after any other core party code (PartyService & PartyPlugin)
	public void onUserJoin(UserJoin userJoin) {
		//Also runs for every person in the party when joining, so can potentially skip the PartyChanged code.
		//Specifically opted to use this approach instead of partyService.isInParty() && partyService.getMemberByDisplayName(player.getName()) != null
		//Usernames will persist till hopping/logout, even if the local player or a partyMember leaves the party
		long memberId = userJoin.getMemberId();
		String standardizedUsername = Text.standardize(partyService.getMemberById(memberId).getDisplayName());
		if (!Strings.isNullOrEmpty(standardizedUsername)) {
			runelitePartyStandardizedUsernames.add(standardizedUsername);
		} else { //In case the party service can't get the display name yet, add to memberId to hashset and retry for 5 gameticks.
			partyMemberIds.add(memberId);
			setAddUserJoinedPartyStandardizedUsernamesFlag();
		}
	}

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned playerSpawned) {
		//Processing the widget inside cox does not work because the data is not transferred if the interface is not opened... Additionally, there is no widget when outside the raid and there is no interesting scriptId that runs while the player is outside.
		//Varbits.IN_RAID gets updated to 1 when joining the CoX underground lobby! When leaving the underground lobby, it gets set back to 0. Thus, if it's 1, the player is in the underground lobby or doing a CoX raid. isInFC check is not required because people have to be in the raiding party when this varbit is 1 (CoX is instanced).
		//Varbit gets set after PlayerSpawned fired, so getCoXPlayers is also ran when the varbit changes.
		if (inCoXRaidOrLobby) { //If Varbits.IN_RAID > 0
			raidPartyStandardizedUsernames.add(Text.standardize(playerSpawned.getPlayer().getName())); //Standardize playername that joined cox lobby / cox raid and add to hashset.
		}
	}

	private void convertCommaSeparatedConfigStringToSet(String configString, HashSet<String> setToConvertTo) {
		//todo: test if changing this to a hashset has any implications
		//Convert a CSV config string to a set
		setToConvertTo.clear();
		setToConvertTo.addAll(Text.fromCSV(Text.standardize(configString)));
	}

	private void getCoXVarbit() {
		if (client.getGameState() == GameState.LOGGED_IN) {
			inCoXRaidOrLobby = client.getVarbitValue(Varbits.IN_RAID) > 0; //Convert the int to boolean. 0 = false, 1 = true.
		}
	}

	private void getCoXPlayers() {
		//Get all players when inside CoX lobby or CoX raid and add them to the hashset after standardizing the name
		//Useful when starting the plugin inside CoX or when clearing the raid party inside CoX (doesn't proc PlayerSpawned)
		if (inCoXRaidOrLobby && client.getGameState() == GameState.LOGGED_IN) { //If Varbits.IN_RAID > 0
			List<Player> playersCoX = client.getPlayers();
			for (Player player: playersCoX) {
				raidPartyStandardizedUsernames.add(Text.standardize(player.getName()));
			}
		}
	}

	//Varbits.THEATRE_OF_BLOOD (6440): Theatre of Blood 1=In Party, 2=Inside/Spectator, 3=Dead Spectating
	private void getToBPlayers() {
		//Adds the ToB players to the Raid hashset. Useful when resetting the list and updating it again so the old ToA players don't join.
		//The varcs do not get cleared if the player leaves, so check if the player is inside tob first.
		//However, when joining a new raid, the varcStrings get updated. E.g. first do a raid with 4 people, then a duo tob => upon entering, player 3 and 4 strings will be emptied.
		if (client.getVarbitValue(Varbits.THEATRE_OF_BLOOD) > 1) {
			for (int i = 0; i < 5; i++) {
				addInRaidUsernamesVarClientStr(TOB_IN_RAID_VARCSTR_PLAYER1_INDEX + i);
			}
		}
	}

	//TOA_IN_RAID_VARPID Very likely the in raid ToA varp. Can't find anything about it in CS2 scripts and Cook has not updated chisel's varbs to see it.
	//It changes to e.g. 1001 when entering ToA, then when leaving it does: 1001 -> 1000 -> 1200 -> 0.
	//Could not find any other good alternatives. 3603 does change from -1 to random value that keeps changing when entering the raid and entering rooms, but it already changes from -1 to some random value while in the ToA lobby (after joining a party).
	private void getToAPlayers() {
		//Adds the ToA players to the Raid hashset. Useful when resetting the list and updating it again so the old ToB players don't join.
		//The varcs do not get cleared if the player leaves, so check if the player is inside ToA first.
		//However, when joining a new raid, the varcStrings get updated. E.g. first do a raid with 4 people, then a duo ToA => upon entering, player 3 and 4 strings will be emptied.
		if (client.getVarpValue(TOA_IN_RAID_VARPID) > 0) {
			for (int i = 0; i < 8; i++) {
				addInRaidUsernamesVarClientStr(TOA_IN_RAID_VARCSTR_PLAYER1_INDEX + i);
			}
		}
	}

	private void addClanMembers(ClanChannelChanged clanChannelChanged, ClanSettings clanSettings, HashSet<String> clanHashSet) {
		if (clanSettings != null) {
			//Adds all the members to the HashSet (according to the clan settings)
			List<ClanMember> clanMembers = clanSettings.getMembers();
			for (ClanMember clanMember : clanMembers) {
				clanHashSet.add(Text.standardize(clanMember.getName()));
			}
		}

		//Clan members get added via the clan settings, but clan/guest clan guests are not a part of those.
		if (clanChannelChanged.getClanChannel() != null) {
			//Previous solution does not add the guests that are already in the CC, also add those
			List<ClanChannelMember> clanMembersOnline = clanChannelChanged.getClanChannel().getMembers();
			for (ClanChannelMember clanMember : clanMembersOnline) {
				clanHashSet.add(Text.standardize(clanMember.getName()));
			}
		}
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
	private Set<ChatTabFilterOptions2D> componentIDToChatTabFilterSet2D(int componentID) {
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
		if (client.getGameState() == GameState.LOGGED_IN) {
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
		boolean[] filtersEnabled = new boolean[]{publicFilterEnabled, privateFilterEnabled, channelFilterEnabled, clanFilterEnabled, tradeFilterEnabled}; //If you need to use this somewhere else, try making it private static and updating it in onConfigChanged
		//Iterate through all chat filter enabled booleans and check if they should be active according to the config or not
		for (int i = 0; i < filtersEnabled.length; i++) {
			if (filtersEnabled[i] && !shouldFilterChatType(chatboxComponentIDs.get(i))) {
				executeSetChatFilterConfig(chatboxComponentIDs.get(i), false);
				shouldRedraw = true;
			}
		}
		//If a chat filter has been disabled because the config set has been emptied, redraw all chat buttons, then set the Custom text again for all active ones
		if (shouldRedraw) {
			redrawChatButtons(); //setChatStoneWidgetTextAll() is not required because that already procs when REDRAW_CHAT_BUTTONS_SCRIPTID is procced
		}
	}

	private void setConfigFirstStart() {
		//todo: if changing config stuff that's not in ChatFilterExtendedConfig (but only set by configmanager), change this as well
		//Config keys that are not part of ChatFilterExtendedConfig are still empty on first startup. Prevent them being null by setting them before other code checks the config keys.
		for (String filtersEnabledString : filtersEnabledStringList) {
			if (configManager.getConfiguration(configGroup, filtersEnabledString) == null) {
				configManager.setConfiguration(configGroup, filtersEnabledString, false);
			}
		}
	}

	private void clearRaidPartyHashset() {
		previousRaidPartyInterfaceText = "";
		raidPartyStandardizedUsernames.clear();
		//Rebuild the raid party hashset by adding the current people to it. Events that run every gametick (either via onGameTick or e.g. via a script that runs every gametick, are excluded here since they'll run anyway).
		//Thus, CoX bank is excluded, ToB/ToA lobby party interface is excluded.
		getCoXPlayers(); //Get CoX players because it does not trigger onPlayerSpawned while inside a raid.
		processToBBoard(); //Person might close the interface before the script procs.
		processToABoard(); //Person might close the interface before the script procs.
		getToBPlayers(); //Checks if player is inside ToB to only add them then. Use addAllInRaidUsernamesVarClientStr() if you also want to add when outside ToB or old ToA players
		getToAPlayers(); //Checks if player is inside ToA to only add them then. Use addAllInRaidUsernamesVarClientStr() if you also want to add when outside ToA or old ToB players
	}

	private boolean isComponentIDChatStone(int componentID) {
		return chatboxComponentIDs.contains(componentID);
	}

	private void enableChatFilter(MenuEntry menuEntry) {
		setChatFilterConfig(menuEntry, true);
		setChatsToPublic();
	}

	private void setChatFilterConfig(MenuEntry menuEntry, boolean enableFilter) {
		int menuComponentID = menuEntry.getParam1();
		if (isComponentIDChatStone(menuComponentID)) {
			executeSetChatFilterConfig(menuComponentID, enableFilter);
		}
	}

	private void executeSetChatFilterConfig(int componentID, boolean enableFilter) {
		//Separate method, so it can be easily run by putting in the componentID instead having to enter a MenuEntry
		//publicFilterEnabled = enableFilter is not necessary since ConfigManager does trigger updateConfig() if the config value actually gets changed from false to true or vice versa
		//Alternatively use a switch(componentID) statement like you did before. It's probably more efficient execution wise, but we got these lists anyway and this is more compact
		for (int i = 0; i < chatboxComponentIDs.size(); i++) {
			if (chatboxComponentIDs.get(i) == componentID) {
				configManager.setConfiguration(configGroup, filtersEnabledStringList.get(i), enableFilter);
			}
		}
	}

	private void setChatsToPublic() {
		//Set the flag that set chat tabs to public, so they can then be filtered by the plugin
		//Flag is reset in onGameTick
		setChatsToPublicFlag = true;
		setChatStoneWidgetTextAll();
	}

	private void executeSetChatsToPublic() {
		//Set chat tabs to public, so they can then be filtered by the plugin
		//client.getGameState check not needed because this is only ran in onGameTick

		//Public, private, trade remember between hops => they are only set when onlyVolatile is set to false.
		//Channel and clan are probs varbit based => they are always set when this method is executed ("volatile").
		//Channel, Clan don't remember between hopping; potentially related to varbits as described here https://discord.com/channels/301497432909414422/301497432909414422/1086022946633547867 (i.e. I suspect when hopping it reads the state from the varbits and then sets the chat filters according to those values)
		clientThread.invokeLater(() -> {
			//Could potentially put this nicely in an enum at some point. Should probably also document what other arguments do then. 1 might be game but it's not listed in script 184 proc,chat_set_filter. Regarding 2nd argument it's just always +1 to get the next MenuOption and if you enter a value that's too high, it goes to show all/on (for some chat tabs?)
			if (publicFilterEnabled) {
				client.runScript(CHAT_SET_FILTER_SCRIPTID, 2, 0);
				//2 = public tab, 0 = show all
			}
			if (privateFilterEnabled && forcePrivateOn) {
				client.runScript(CHAT_SET_FILTER_SCRIPTID, 3, 0);
				//3 = private tab, 0 = show all
			}
			if (channelFilterEnabled) {
				client.runScript(CHAT_SET_FILTER_SCRIPTID, 4, 0);
				//4 = chat channel tab, 0 = show all
			}
			if (clanFilterEnabled) {
				client.runScript(CHAT_SET_FILTER_SCRIPTID, 5, 0);
				//5 = clan tab, 0 = show all
			}
			if (tradeFilterEnabled) {
				client.runScript(CHAT_SET_FILTER_SCRIPTID, 6, 0);
				//6 = trade tab, 0 = show all
			}
		});
	}

	private void setChatStoneWidgetTextAll() {
		//Sets the WidgetText for enabled chats to Custom
		if (client.getGameState() != GameState.LOGGED_IN) {
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
		widget.getStaticChildren()[2].setText(customTextString); //or e.g. chatWidget.getStaticChildren().length-1 but that change more often idk
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
		//Should the message be filtered, based on ChatMessageType and the sender's name.
		//For overheads, check shouldFilterMessage2D!
		Set<ChatTabFilterOptions> chatTabHashSet = chatMessageTypeToChatTabFilterOptions(chatMessageType);
		if (chatTabHashSet == null || chatTabHashSet.isEmpty()) {
			return false;
		}
		//From hereon, the ChatMessageType has the filter active

		//Set = todo: get the hashset here based on the ChatMessage type
		//and then you should make commands to return all these hashsets:
		/*
		private static Set<ChatTabFilterOptions> publicChatFilterOptions = new HashSet<>();
		private static Set<ChatTabFilterOptions2D> publicChatFilterOptions2D = new HashSet<>();
		private static final HashSet<String> publicWhitelist = new HashSet<>();
		private static Set<ChatTabFilterOptions> privateChatFilterOptions = new HashSet<>();
		private static final HashSet<String> privateWhitelist = new HashSet<>();
		private static Set<ChatTabFilterOptions> channelChatFilterOptions = new HashSet<>();
		private static final HashSet<String> channelWhitelist = new HashSet<>();
		private static Set<ChatTabFilterOptions> clanChatFilterOptions = new HashSet<>();
		private static final HashSet<String> clanWhitelist = new HashSet<>();
		private static Set<ChatTabFilterOptions> tradeChatFilterOptions = new HashSet<>();
		private static final HashSet<String> tradeWhitelist = new HashSet<>();
		 */

		playerName = Text.standardize(playerName); //Very likely works considering other methods work with a standardized name. Can't test this though since my name doesn't have e.g. a space.
		if (playerName.equals(Text.standardize(client.getLocalPlayer().getName()))) {
			return false;
		}
		if (showFriendsMessages && client.isFriended(playerName, false)) { //todo: completely rework this stuff probs + zie google calendar!!
			return false;
		}
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
		} //todo: add custom whitelist && rl party & public & alle 2d gedoe
		return true;
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
	}

	private boolean isChatTabCustomFilterActiveChatMessageType(ChatMessageType chatMessageType) { //todo: remove probs
		//Returns true if the chat tab is set to Show: custom, based on the ChatMessageType
		if (chatMessageType != null) {
			switch (chatMessageType) {
				//AUTOTYPER	is not shown/is filtered on public = on anyway
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
					//TRADE and TRADE_SENT are not received when someone tries to trade you, only TRADEREQ
					return tradeFilterEnabled;
			}
		}
		return false;
	}

	@Nullable
	private Set<ChatTabFilterOptions> chatMessageTypeToChatTabFilterOptions(ChatMessageType chatMessageType) {
		//Translates the ChatMessageType to the appropriate hashset (not 2D, so not for overheads).
		if (chatMessageType != null) {
			switch (chatMessageType) {
				//AUTOTYPER	is not shown/is filtered on public = on anyway
				case PUBLICCHAT:
				case MODCHAT:
					return publicChatFilterOptions;
				case PRIVATECHAT:
				case MODPRIVATECHAT:
					return privateChatFilterOptions;
				case FRIENDSCHAT:
					return channelChatFilterOptions;
				case CLAN_CHAT:
				case CLAN_GIM_CHAT:
				case CLAN_GUEST_CHAT:
					return clanChatFilterOptions;
				case TRADEREQ:
					//TRADE and TRADE_SENT are not received when someone tries to trade you, only TRADEREQ
					return tradeChatFilterOptions;
			}
		}
		return null;
	}

	/**
	 * rest van de plugin: gebruik isclanchatmember (wat als op leave cc, wat als leave cc en dan client herstarten?, wat als guest cc? wat als iemand jouw cc joint als guest), is friendschatmember potentieel aanvullend aan bestaande oplossingen, denk na over wanneer lists gecleared moeten worden
	 * aanvullend aan bestaande oplossingen, denk na over wanneer lists gecleared moeten worden
	 */

	//private static final int TOB_BOARD_ID = 50; //N50.0
	//private static final int TOP_HALF_TOB_BOARD_CHILDID = 27; //S50.27
	//private static final int BOTTOM_HALF_TOB_BOARD_CHILDID = 42; //S50.42
	//Top part of the ToB board names are dynamic children of 50.27, e.g. D50.27[0], D50.27[1], D50.27[2] etc.
	//[0], [11], [22] etc is the whole line; these are useless for info but always type 3.
	//[1], [12], [23] etc are the usernames. These are type 4. (The levels etc. are also type 4; thus it's type 3 followed by a lot of type 4s of which the first is a username)
	//Bottom part of the ToB board names are dynamic children of 50.42, e.g. D50.42[0], D50.42[1], [2], [4], [6], [8], [10], [12], [14], [16], [18], [20], [21], [22], [24]
	//[0] and [20] are the whole lines again, type 3.
	//[1] and [21] are the usernames, type 4.
	private void processToBBoard() {
		Widget topHalfToBBoardWidget = client.getWidget(TOB_BOARD_ID, TOP_HALF_TOB_BOARD_CHILDID);
		Widget bottomHalfToBBoardWidget = client.getWidget(TOB_BOARD_ID, BOTTOM_HALF_TOB_BOARD_CHILDID);
		processBoard(topHalfToBBoardWidget, bottomHalfToBBoardWidget);
	}
	//todo: maybe add clear button to clear raid members and/or other lists? idk. Maybe it should be a config setting to show this menuentry. Mss optie: always, shift+right click, never => default shift+right-click
	//todo: maybe add option to also clear clan, fc etc and some other hashsets but set those to default never => think about it first if this could give problems though... Probably would since it'd miss the current people that joined as guest in the cc/guest cc/current fc members etc

	private void processBoard(Widget topOrMembersPart, Widget bottomOrApplicantsPart) {
		//Since processing the ToB and ToA boards works the same, this method works for both.
		//Please refer to processToBBoard and processToABoard for more info.
		HashSet<String> raidPartyStandardizedUsernamesTemp = new HashSet<>();
		if (topOrMembersPart != null && topOrMembersPart.getDynamicChildren() != null) {
			for (int i = 0; i < topOrMembersPart.getDynamicChildren().length; i++) {
				//Get child that has type 3 => next one has to be the username
				if (topOrMembersPart.getChild(i).getType() == 3) {
					//Index of the one that has name is type 3 index + 1
					Widget nameWidget = topOrMembersPart.getChild(i + 1);
					if (nameWidget.getType() == 4) {
						//If right type (4), get the text and standardize it
						String standardizedRaidUsername = Text.standardize(nameWidget.getText()); //Also removes the leading and trailing spaces from -
						if (!standardizedRaidUsername.equals("-")) { //Skip empty entries and add to temporary HashSet to remember
							raidPartyStandardizedUsernamesTemp.add(standardizedRaidUsername);
						}
					}
				}
			}
		}
		if (bottomOrApplicantsPart != null && bottomOrApplicantsPart.getDynamicChildren() != null) {
			for (int i = 0; i < bottomOrApplicantsPart.getDynamicChildren().length; i++) {
				//Get child that has type 3 => next one has to be username
				if (bottomOrApplicantsPart.getChild(i).getType() == 3) {
					//Index of the one that has name is type 3 index + 1
					Widget nameWidget = bottomOrApplicantsPart.getChild(i + 1);
					if (nameWidget.getType() == 4) {
						//If right type (4), get the text and standardize it, then add it to the temp hashset
						//Skipping empty entries ("-") is not required since they are not added to the bottom half of the board (those dynamic children just don't exist).
						raidPartyStandardizedUsernamesTemp.add(Text.standardize(nameWidget.getText()));
					}
				}
			}
		}
		//If it's the user's party/the user applied, add the temporary HashSet to the real HashSet
		if (client.getLocalPlayer() != null && raidPartyStandardizedUsernamesTemp.contains(Text.standardize(client.getLocalPlayer().getName()))) {
			raidPartyStandardizedUsernames.addAll(raidPartyStandardizedUsernamesTemp);
		}
	}

	//private static final int TOB_PARTY_INTERFACE_NAMES_CHILDID = 12; //S28.12
	//No party text = -<br>-<br>-<br>-<br>-
	//Party text = Username<br>Username2<br>-<br>-<br>-
	private void processToBPartyInterface() {
		Widget tobPartyInterfaceNamesWidget = client.getWidget(InterfaceID.TOB, TOB_PARTY_INTERFACE_NAMES_CHILDID); //S28.12
		processRaidPartyInterface(tobPartyInterfaceNamesWidget);
	}

	private void processRaidPartyInterface(Widget partyInterfaceNamesWidget) {
		if (partyInterfaceNamesWidget != null && !partyInterfaceNamesWidget.isHidden()) { //Widget is hidden among others inside ToB while the script will still proc inside ToB.
			String raidPartyInterfaceText = partyInterfaceNamesWidget.getText();
			//Only process the widget if the text has changed compared to the previous processing
			if (!raidPartyInterfaceText.equals(previousRaidPartyInterfaceText)) {
				previousRaidPartyInterfaceText = raidPartyInterfaceText;
				raidPartyInterfaceText = raidPartyInterfaceText.concat("<br>"); //Append <br> so indexOf and substring works for every item
				for (int i = 0; i < 8; i++) {
					int idx = raidPartyInterfaceText.indexOf("<br>");
					if (idx != -1) {
						String standardizedUsername = Text.standardize(raidPartyInterfaceText.substring(0, idx));
						//Prevent empty strings or strings equalling "-" being added to the hashset.
						if (!Strings.isNullOrEmpty(standardizedUsername) && !standardizedUsername.equals("-")) {
							//Since the user has to be in this party (can't view other parties like this), add to the real HashSet instead of a temp one
							raidPartyStandardizedUsernames.add(standardizedUsername);
						}
						raidPartyInterfaceText = raidPartyInterfaceText.substring(idx + 4); //get substring to remove first user and first <br> (idx+4 so resulting substring starts after the first <br>)
					}
				}
			}
		}
	}

	private void addAllInRaidUsernamesVarClientStr() {
		//Add all standardized names of TOB and TOA raiders to a hashset.
		//Useful for when starting the plugin while inside TOB/TOA.
		for (int i = 0; i < 5; i++) {
			addInRaidUsernamesVarClientStr(TOB_IN_RAID_VARCSTR_PLAYER1_INDEX + i);
		}
		for (int i = 0; i < 8; i++) {
			addInRaidUsernamesVarClientStr(TOA_IN_RAID_VARCSTR_PLAYER1_INDEX + i);
		}
	}

	private void addInRaidUsernamesVarClientStr(int varCStrIndex) {
		//Add the standardized names of TOB and TOA raiders to a hashset.
		//Hashset can't contain dupes and the add method already checks if it contains it or not.
		//If tob or toa VarCStrIndex
		if ((varCStrIndex >= TOB_IN_RAID_VARCSTR_PLAYER1_INDEX && varCStrIndex <= TOB_IN_RAID_VARCSTR_PLAYER5_INDEX)
				|| (varCStrIndex >= TOA_IN_RAID_VARCSTR_PLAYER1_INDEX && varCStrIndex <= TOA_IN_RAID_VARCSTR_PLAYER8_INDEX)) {
			String varCStrValueStandardized = Text.standardize(client.getVarcStrValue(varCStrIndex));
			//isNullOrEmpty check because they get refreshed in probably every room and can potentially add empty strings to the hashset.
			if (!Strings.isNullOrEmpty(varCStrValueStandardized)) {
				raidPartyStandardizedUsernames.add(varCStrValueStandardized);
			}
		}
	}

	//private static final int TOA_BOARD_ID = 774; //S774.32
	//private static final int MEMBERS_TOA_BOARD_CHILDID = 32; //S774.32
	//private static final int APPLICANTS_TOA_BOARD_CHILDID = 48; //S774.48
	//Member tab of the ToA board names are dynamic children of S774.32, e.g. D774.32[0], D774.32[1], D774.32[2] etc.
	//[0], [13], [26], [39] etc is the whole line; these are useless for info but always type 3.
	//[1], [14], [27], [40] etc are the usernames. These are type 4. (The levels etc. are also type 4; thus it's type 3 followed by a lot of type 4s of which the first is a username)
	//Applicants tab of the ToA board names are dynamic children of S774.48, e.g. D774.48[0], D774.48[1], [2], [4], [6], [8], [10], [12], [14], [16], [18], [20], [21], [22], [24] etc
	//[0] and [20] are the whole lines again, type 3.
	//[1] and [21] are the usernames, type 4.
	private void processToABoard() {
		//When you are on a different tab than Members (Applicants, Invocations, Summary), the widget is hidden but not null! Thus, even while on a different tab, you can get the current members. Those are needed to determine if it is your party.
		Widget membersToABoardWidget = client.getWidget(TOA_BOARD_ID, MEMBERS_TOA_BOARD_CHILDID);
		Widget applicantsToABoardWidget = client.getWidget(TOA_BOARD_ID, APPLICANTS_TOA_BOARD_CHILDID);
		processBoard(membersToABoardWidget, applicantsToABoardWidget);
		//todo: tob + toa check if widgetids, scriptids al ergens in runelite bestaan of niet
	}

	//private static final int TOA_PARTY_INTERFACE_NAMES_CHILDID = 5; //S773.5
	//No party text = -<br>-<br>-<br>-<br>-<br>-<br>-<br>-
	//Party text = Username<br>Username2<br>-<br>-<br>-<br>-<br>-<br>-
	private void processToAPartyInterface() {
		Widget toaPartyInterfaceNamesWidget = client.getWidget(InterfaceID.TOA_PARTY, TOA_PARTY_INTERFACE_NAMES_CHILDID); //S773.5
		processRaidPartyInterface(toaPartyInterfaceNamesWidget);
	}

	/*
	Varp IN_RAID_PARTY: The ID of the party. This Var is only set in the raid bank area and the raid lobby.
	This gets set to -1 when the raid starts and when leaving the raid bank area.
	This is first set when the first player of the friends chat forms a party on the recruiting board, and it changes again when the first person actually enters the raid.
	-1: Not in a party or in the middle of an ongoing raid.
	Anything else: This means that your friends chat has a raid party being formed and has not started yet.
	Does get changed from e.g. -1 to a value if a user walks up the stairs to the cox bank area, or teleports in or out (tele out = set to -1 again).
	 */
	private void getCoXBankPlayers() {
		//Procs every gametick while in the cox bank regionId. Check varp so it only procs in the bank area.
		//Cox bank people can technically not be in the FC yet when spawning or run up the CoX stairs with you so execute every gametick instead of onplayerspawned
		if (client.getVarpValue(VarPlayer.IN_RAID_PARTY) > -1) {
			List<Player> players = client.getPlayers();
			FriendsChatManager friendsChatManager = client.getFriendsChatManager();
			if (friendsChatManager != null) {
				for (Player player : players) {
					String standardizedUsername = Text.standardize(player.getName());
					if (friendsChatManager.findByName(standardizedUsername) != null && !Strings.isNullOrEmpty(standardizedUsername)) {
						raidPartyStandardizedUsernames.add(standardizedUsername);
					}
				}
			}
		}
	}

	private void setAddPartyMemberStandardizedUsernamesFlag() {
		//partyService.getMembers() is empty when immediately running this after joining a party. Set a flag to retry 5 gameticks or till the list is not empty.
		getRLPartyMembersFlag = 5;
		addPartyMemberStandardizedUsernames();
	}

	private void addPartyMemberStandardizedUsernames() {
		//Opted to use this so party members would remain until hopping.
		//Alternatively just use partyService.isInParty() && partyService.getMemberByDisplayName(player.getName()) != null in the shouldFilter code if you only want it to be when they are in the party.
		if (partyService.isInParty()) {
			List<PartyMember> partyMembers = partyService.getMembers();
			if (partyMembers != null && !partyMembers.isEmpty()) {
				getRLPartyMembersFlag = 0;
				for (PartyMember partyMember : partyMembers) {
					String standardizedUsername = Text.standardize(partyMember.getDisplayName());
					if (!Strings.isNullOrEmpty(standardizedUsername)) {
						runelitePartyStandardizedUsernames.add(standardizedUsername);
					}
				}
				System.out.println(runelitePartyStandardizedUsernames);
			}
		}
	}

	private void setAddUserJoinedPartyStandardizedUsernamesFlag() {
		//getDisplayName is sometimes not yet available onUserJoined, while the memberId is. So set a flag to retry.
		getRLPartyUserJoinedMembersFlag = 5;
		addUserJoinedPartyStandardizedUsernames();
	}

	private void addUserJoinedPartyStandardizedUsernames() {
		//If username could not be determined onUserJoin, the memberIds were added to a hashset.
		//Go through the hashset, add the standardized usernames to the hashset.
		boolean allMembersProcessed = true;
		for (long memberId : partyMemberIds) {
			String standardizedUsername = Text.standardize(partyService.getMemberById(memberId).getDisplayName());
			if (!Strings.isNullOrEmpty(standardizedUsername)) {
				runelitePartyStandardizedUsernames.add(standardizedUsername);
			} else {
				//If a member is not processed correctly, set the boolean to false
				allMembersProcessed = false;
			}
		}
		//If no member has been processed too early (if the boolean has not been set to false), set flag to 0
		if (allMembersProcessed) {
			getRLPartyUserJoinedMembersFlag = 0;
		}
		System.out.println(runelitePartyStandardizedUsernames);
		if (getRLPartyUserJoinedMembersFlag == 0) { //Clear the hashset again when flag = 0. Set void sets flag back to 5 in case a user joins while the flag is timing down.
			partyMemberIds.clear();
		}
	}

	//todo: make filter code probs (hangt tevens met list etc samen) => voor friends zie chatfilter plugin iig! + test dit ingame
	//todo: Add own displayname + ook wanneer onacchashchanged en die niet null is. +denk aan cox gedoe zoals de interface bij fc of mensen die in je raid zijn of zo + separate config options voor chatbox vs overhead + rebuild chatbox enzo als config options aangepast zijn + rebuild als mensen aan de lijst zijn toegevoegd (zijn oude messages van voor de add to list dan ook zichtbaar?)

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

/*SmartChatInput problematic interaction error:
2023-11-09 06:13:06 CET [Client] WARN  n.runelite.client.eventbus.EventBus - Uncaught exception in event subscriber
java.lang.NullPointerException: Cannot invoke "String.contains(java.lang.CharSequence)" because "name" is null
	at com.smartchatinputcolor.SmartChatInputColorPlugin.deriveChatChannel(SmartChatInputColorPlugin.java:129)
	at com.smartchatinputcolor.SmartChatInputColorPlugin.recolorChatTypedText(SmartChatInputColorPlugin.java:98)
	at com.smartchatinputcolor.SmartChatInputColorPlugin.onScriptPostFired(SmartChatInputColorPlugin.java:262)
	at net.runelite.client.eventbus.EventBus$Subscriber.invoke(EventBus.java:70)
	at net.runelite.client.eventbus.EventBus.post(EventBus.java:223)
	at net.runelite.client.callback.Hooks.post(Hooks.java:194)
	at client.xf(client.java:4992)
	at cm.ar(cm.java:16152)
	at mo.ah(mn.java:5782)
	at client.yh(client.java)
	at client.ol(client.java:46062)
	at client.runScript(client.java:7425)
	at com.ywcode.chatfilterextended.ChatFilterExtendedPlugin.lambda$setChatsToPublic$3(ChatFilterExtendedPlugin.java:678)
	at net.runelite.client.callback.ClientThread.lambda$invokeLater$1(ClientThread.java:80)
	at net.runelite.client.callback.ClientThread.invokeList(ClientThread.java:119)
	at net.runelite.client.callback.ClientThread.invoke(ClientThread.java:101)
	at net.runelite.client.callback.Hooks.tick(Hooks.java:218)
	at client.km(client.java:34819)
	at client.bj(client.java)
	at bm.an(bm.java:394)
	at bm.ib(bm.java)
	at bm.run(bm.java:52825)
	at java.base/java.lang.Thread.run(Thread.java:833)

	Fixed by just setting a flag and running onGameTick. It was causing the SCIC code to be executed earlier than it normally is, so player.getName was null
 */

/* (very old) scriptIds tob board as reference:
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

/*
getName() = <img=41>major�leach
Text.removeTags = major�leach
Text.standardize = major leach
 */