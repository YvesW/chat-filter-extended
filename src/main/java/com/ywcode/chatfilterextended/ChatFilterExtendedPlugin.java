package com.ywcode.chatfilterextended;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanID;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.ClanMemberJoined;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.FriendsChatChanged;
import net.runelite.api.events.FriendsChatMemberJoined;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@PluginDescriptor(
        name = "Chat Filter Extended",
        description = "Extends the functionality of the chat tabs/stones to filter chat messages not from friends/FC members/clan members/Guest CC members/raid members/RL party members/whitelisted people.",
        tags = {"chat,chat filter,public,public OH,friends,friends OH,fc,fc OH,cc,cc OH,guest,guest OH,raid,raid OH,party,party OH,whitelist,whitelist OH,custom,clanchat,clan,filter,friends chat,private,trade,raids,tob,toa,cox,spam,show,chat tab,chat stone"}
)
//Alternative (bad) names: Custom Chat Filter, Custom Chat View, Chat View Extended, Chat Show Custom, Chat tabs extended, Chatstones extended
//My goal was not to make one of these "abc improved" or "better abc" plugins, but the menuOptions like "Show friends" or "Show none" are just called chat filters, and I can't come up with a better name. At least Polar calls them that in e.g. script 152 (chat_set_filter)
//It's just unlucky that the chat filter plugin (which is a perfectly valid name for its function) is also called chat filter, I guess.

public class ChatFilterExtendedPlugin extends Plugin {

    // ------------- Wall of config vars -------------
    // Vars are quite heavily cached so could probably just config.configKey(). However, the best practice behavior in plugins is to have a bunch of variables to store the results of the config methods, and check it in startUp/onConfigChanged. It feels redundant, but it's better than hitting the reflective calls every frame. --LlemonDuck. Additionally, the whitelist strings are actually getting processed.
    private static Set<ChatTabFilterOptions> publicChatFilterOptions = new HashSet<>();
    private static Set<ChatTabFilterOptionsOH> publicChatFilterOptionsOH = new HashSet<>();
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
    private static boolean clearRLPartySetLeave;
    private static boolean fixChatTabAlert;
    private static boolean preventLocalPlayerChatTabAlert; //todo: implement!
    private static ShiftMenuSetting changeChatSetsShiftMenuSetting;
    private static ShiftMenuSetting clearRaidPartyShiftMenuSetting;

    //The config values below are only set through ConfigManager and are not part of ChatFilterExtendedConfig.java
    private static boolean publicFilterEnabled; //i.e. if the user set the chat tab/stone to custom. So we can re-enable it on startup. Maybe swap this to RSProfile instead of config profile in the future?
    private static boolean privateFilterEnabled; //i.e. if the user set the chat tab/stone to custom. So we can re-enable it on startup. Maybe swap this to RSProfile instead of config profile in the future?
    private static boolean channelFilterEnabled; //i.e. if the user set the chat tab/stone to custom. So we can re-enable it on startup. Maybe swap this to RSProfile instead of config profile in the future?
    private static boolean clanFilterEnabled; //i.e. if the user set the chat tab/stone to custom. So we can re-enable it on startup. Maybe swap this to RSProfile instead of config profile in the future?
    private static boolean tradeFilterEnabled; //i.e. if the user set the chat tab/stone to custom. So we can re-enable it on startup. Maybe swap this to RSProfile instead of config profile in the future?
    // ------------- End of wall of config vars -------------

    //Variables
    //todo: probably sort these
    private static boolean shuttingDown; //Default value is false
    private static boolean setChatsToPublicFlag; //Default value is false
    private static GameState previousPreviousGameState;
    private static GameState previousGameState;
    private static final HashSet<String> channelStandardizedUsernames = new HashSet<>();
    private static final HashSet<String> clanStandardizedUsernames = new HashSet<>();
    private static final HashSet<String> guestClanStandardizedUsernames = new HashSet<>();
    private static final HashSet<String> raidPartyStandardizedUsernames = new HashSet<>();
    private static final HashSet<String> runelitePartyStandardizedUsernames = new HashSet<>();
    private static boolean inCoXRaidOrLobby; //Default value is false
    private static int getRLPartyMembersFlag; //Default is 0
    private static boolean shouldRefreshChat; //Default is false
    private static String previousRaidPartyInterfaceText; //null by default
    private static final HashSet<Long> partyMemberIds = new HashSet<>();
    private static int getRLPartyUserJoinedMembersFlag; //Default is 0
    private final List<ChatTabAlert> chatTabAlerts = new ArrayList<>(); //todo: potentially remove if switching to other alert solution

    //Collection cheat sheet: https://i.stack.imgur.com/POTek.gif (that I probably did not fully adhere to lol)

    //Constants
    //todo: probably sort these
    private static final String configGroup = "ChatFilterExtended";
    private static final List<Integer> chatboxComponentIDs = ImmutableList.of(ComponentID.CHATBOX_TAB_PUBLIC, ComponentID.CHATBOX_TAB_PRIVATE, ComponentID.CHATBOX_TAB_CHANNEL, ComponentID.CHATBOX_TAB_CLAN, ComponentID.CHATBOX_TAB_TRADE);
    private static final List<String> filtersEnabledStringList = ImmutableList.of("publicFilterEnabled", "privateFilterEnabled", "channelFilterEnabled", "clanFilterEnabled", "tradeFilterEnabled");
    private static final String TAB_CUSTOM_TEXT_STRING = "<br><col=ffff00>Custom</col>";
    private static final int TOA_LOBBY_REGION_ID = 13454; //Region id of ToA lobby (which has the bank)
    private static final int COX_BANK_REGION_ID = 4919; //Region id of CoX bank
    private static final int IN_A_RAID_VARPID = 2926; //Changes to e.g. 1001 when entering a raid (ToA, CoX, ToB; does apparently not proc for vork or PNM), then when leaving it does: 1001 -> 1000 -> 1200 -> 0.
    private static final int TOA_PARTY_VARPID = 3603; //-1 when not in a party, anything else when in a party or in the raid. Alternatively use Varbit 14345: based on proc,toa_lobby_update_header (script 6613): 2 = the party text says "Step Inside Now!", 0 = "No Party", anything else (1) = "Party". Based on my ingame testing: 0 not in a party, 1 when in a party. So anything else is very likely 1 in this case. Will also be 1 in e.g. the lobby when in a solo party.
    private static final int REDRAW_CHAT_BUTTONS_SCRIPTID = 178; //[proc,redraw_chat_buttons]
    private static final int CHAT_SET_FILTER_SCRIPTID = 152; //[clientscript,chat_set_filter]
    private static final int TOB_PARTYDETAILS_BACK_BUTTON_SCRIPTID = 4495; //[proc,tob_partydetails_back_button]
    private static final int TOA_PARTYDETAILS_BACK_BUTTON_SCRIPTID = 6765; //[proc,toa_partydetails_back_button]
    private static final int TOB_BOARD_ID = 50; //N50.0
    private static final int TOP_HALF_TOB_BOARD_CHILDID = 27; //S50.27
    private static final int BOTTOM_HALF_TOB_BOARD_CHILDID = 42; //S50.42
    private static final int TOB_HUD_DRAW_SCRIPTID = 2297; //proc,tob_hud_draw Does proc every tick outside though and also (less frequently) in the raid.
    private static final int TOB_PARTY_INTERFACE_NAMES_CHILDID = 12; //S28.12
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
    private static final int CHAT_ALERT_ENABLE_SCRIPTID = 180; //proc,chat_alert_enable See the tests directory (docs/testing/ChatTab blinking scripts) for an explanation regarding what script does what for chat alerts.
    private static final int PUBLIC_VARC_INT_COUNTDOWN_ID = 45; //Game chat = 44. See script 183 CS2 code.
    private static final int PRIVATE_VARC_INT_COUNTDOWN_ID = 46;
    private static final int FC_VARC_INT_COUNTDOWN_ID = 438; //Yes, 438 is correct
    private static final int CC_VARC_INT_COUNTDOWN_ID = 47;
    private static final int TRADE_VARC_INT_COUNTDOWN_ID = 48;
    //todo: potentially re-add if switching to other alert solution    private static int currentChatTabAlertTab; //1 = game. 2 = public. 3 = friends but does not show up when private is split (which is good, because the tab does also not flash then!). 4 = fc. 5 = cc. 6 = trade.

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
        setConfigFirstStart();
        updateConfig();
        setChatsToPublic(); //Chats are only being set to public if the filter for that chatstone is active!
        addAllInRaidUsernamesVarClientStr(); //Will also add a raid group to the hashset if you are not inside ToB/ToA anymore. This is fine and can be useful in certain situations, e.g. getting a scythe, teleporting to the GE to get the split and then turning on the plugin at the GE. You can still see your raid buddies' messages then. If this is undesired, replace with getToBPlayers() and getToAPlayers()
        setAddPartyMemberStandardizedUsernamesFlag(); //In case the plugin is started while already in a party.
        clientThread.invokeLater(this::getCoXVarbit); //Get varbit in case the plugin is started while logged in.
        clientThread.invokeLater(this::getCoXPlayers); //Get CoX players because it does not trigger onPlayerSpawned while inside a raid if the players have already spawned before the plugin is turned on.
        clientThread.invokeLater(this::processToBBoard); //User might technically enable plugin and exit the ToB board before the refresh scriptid procs.
        clientThread.invokeLater(this::processToABoard); //User might technically enable plugin and exit the ToA board before the refresh scriptid procs.
        clientThread.invokeLater(this::getFCMembers); //In case the plugin is started while already in an FC.
        clientThread.invokeLater(this::getCCMembers); //In case the plugin is started while already in a CC.
        clientThread.invokeLater(this::getGuestCCMembers); //In case the plugin is started while already in a guest CC.

        //todo: prevent tab from flickering if a message is filtered...
        //todo: add readme including a couple webms like musicreplacer has
        //todo: go through problems
        //todo: check and refactor the whole fucking shit
        //todo: add comments
        //todo: to ignore highlights, you'd probs have to get the ChatMessage before ChatNotificationsPlugin, getId the Id, getValue the content of the messageNode, and getName() the username, then save the last xx amount of filtered messages and setValue the value of the node to e.g. " ". Then if client.refreshChat gets triggered, you have to go over the saved values and restore the message nodes if the user is now not filtered and the message is " ".

        //Note: Specifically opted not to a right-click option (config advanced ShiftMenuSettings style) to add/remove a player to a whitelist with a pop-out menu (like inventory tags or MES uses). There is an engine limit of only 7 options being visible when you right click someone in game or one of their messages. Thus, do not implement this pretty niche option.
    }

    @Override
    public void shutDown() {
        //TODO: check if something needs to be added to startup or shutdown + profilechange / rebuild chatbox?
        //todo: potentially do some of this shit or stop filter when swapping profiles? check how other filter plugins do it
        //todo: final tests: login, hopping, toggling on/off plugin, toggling settings on/off, opening clan panels, changing to resizable, after npc chatbox maybe, in/after cutscenes like myths guild
        //todo: remove TEST and remove println
        //todo: test a bit what happens when putting clan and e.g. public to off, then enabling custom, then disabling the plugin => should go back to off for both? probably? and what if you then reboot the client?
        //todo: check if cox, tob, toa widgetids, scriptids, varbits, varcs etc. al ergens in runelite bestaan of niet
        //todo: reenable chat history
        //todo: check what chat filter plugin does.
        // script 175 does this:
        /* ~chat_set_filter($int1, ^chatfilter_friends);
				~redraw_chat_buttons;
				~rebuildchatbox($mesuid2);
				~rebuildpmbox($mesuid2);
         */
        //todo: check if config should be changed: e.g. location, defaults, names, descriptions
        //todo: maybe prevent chat notifications from filtered people but idk
        //todo: als je ooit regionid activatie wil doen, Wrt regionid: maak right click option per tab (idx na clear raids, default shift click only?) met submenu of je custom of custom met opties wil. Check devtools of je dit ook via world map kan maar betwijfel dit, m.n. bij sub regions


        shuttingDown = true; //Might not be necessary but just to be sure it doesn't set it back to custom text since the script procs
        clanStandardizedUsernames.clear();
        channelStandardizedUsernames.clear();
        guestClanStandardizedUsernames.clear();
        runelitePartyStandardizedUsernames.clear();
        clearRaidPartyHashset(); //Also clear the string so the plugin will process the party interface if needed
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
            client.refreshChat(); //Refresh chat when the config changes (enabling/disabling filter, changing filter settings).
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
        publicChatFilterOptionsOH = config.publicChatFilterOptionsOH();
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
        clearRLPartySetLeave = config.clearRLPartySetLeave();
        fixChatTabAlert = config.fixChatTabAlert();
        preventLocalPlayerChatTabAlert = config.preventLocalPlayerChatTabAlert();
        changeChatSetsShiftMenuSetting = config.changeChatSetsShiftMenuSetting();
        clearRaidPartyShiftMenuSetting = config.clearRaidPartyShiftMenuSetting();

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
                if (previousGameState == GameState.LOADING &&
                        (previousPreviousGameState == GameState.LOGGING_IN || previousPreviousGameState == GameState.HOPPING)) {
                    //Alternatively just set a flag on LOGGING_IN and HOPPING lol
                    setChatsToPublic();
                    setAddPartyMemberStandardizedUsernamesFlag(); //This will ensure that the party hashset is also correctly populated after fully logging out but remaining in the party.
                }
                break;
            case LOGIN_SCREEN:
                clanStandardizedUsernames.clear();
                channelStandardizedUsernames.clear();
                guestClanStandardizedUsernames.clear();
                runelitePartyStandardizedUsernames.clear();
                clearRaidPartyHashset(); //Also clear the string so the plugin will process the party interface if needed + shouldRefreshChat = true
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
                shouldRefreshChat = true;
                break;
                //todo: potentially add CONNECTION_LOST; check core chat filter plugin
        }
        previousPreviousGameState = previousGameState; //Yes, I'm a magician with words.
        previousGameState = gameStateChanged.getGameState();
    }

    //todo: continue evaluating code/comments from here onward
    @Subscribe
    public void onFriendsChatChanged(FriendsChatChanged friendsChatChanged) {
        //Remove FC usernames when leaving the FC and when the advanced config option is enabled; also procs when hopping/logging out
        if (!friendsChatChanged.isJoined() && clearChannelSetLeave) {
            channelStandardizedUsernames.clear();
            shouldRefreshChat = true;
        }
    }

    @Subscribe
    public void onFriendsChatMemberJoined(FriendsChatMemberJoined friendsChatMemberJoined) {
        //Also procs while joining an FC for all the members currently in it
        //In the case of HashSet, the item isn't inserted if it's a duplicate => so no .contains check beforehand needed.
        //In case the HashSet already contains the value, it'll return false, so the boolean will not be set to true
        if (channelStandardizedUsernames.add(Text.standardize(friendsChatMemberJoined.getMember().getName()))) {
            shouldRefreshChat = true;
        }
    }

    @Subscribe
    public void onClanChannelChanged(ClanChannelChanged clanChannelChanged) {
        //If left CC => clear own cc usernames HashSet
        ClanChannel clanChannel = clanChannelChanged.getClanChannel();
        if (!clanChannelChanged.isGuest()) { //If left or joined own CC or GIM chat
            int clanId = clanChannelChanged.getClanId();
            if (clanId == ClanID.CLAN) { //If left/joined own CC, separate line because of all the if-then-else statements used here
                if (client.getClanChannel(ClanID.CLAN) == null) { //If not in own CC
                    if (clearClanSetLeave) {
                        clanStandardizedUsernames.clear();
                        shouldRefreshChat = true;
                    }
                } else { //If in own CC
                    //If joined own CC, get members and add the usernames to HashSet
                    addClanMembers(clanChannel, client.getClanSettings(ClanID.CLAN), clanStandardizedUsernames);
                }
            }

            //Also include GIM members in Clan Hashset, untested because no access to a GIM account
            if (clanId == ClanID.GROUP_IRONMAN //If joined/left GIM chat
                    && client.getClanChannel(ClanID.GROUP_IRONMAN) != null) { //If in GIM chat
                addClanMembers(clanChannel, client.getClanSettings(ClanID.GROUP_IRONMAN), clanStandardizedUsernames);
            }

        } else { //If left/joined guest CC
            if (client.getGuestClanChannel() == null) { //If not in guest CC
                if (clearGuestClanSetLeave) { //If left guest CC => clear guest cc usernames HashSet if the advanced config option is enabled
                    guestClanStandardizedUsernames.clear();
                    shouldRefreshChat = true;
                }
            } else { //If joined guest clan
                //If joined guest clan, get members and add the usernames to HashSet
                addClanMembers(clanChannel, client.getGuestClanSettings(), guestClanStandardizedUsernames);
            }
        }
    }

    @Subscribe
    public void onClanMemberJoined(ClanMemberJoined clanMemberJoined) {
        //Add newly joined guests this way since the HashSet does not contain clan/guest clan guests yet
        //In the case of HashSet, the item isn't inserted if it's a duplicate => so no .contains check beforehand.
        ClanChannel clanChannel = client.getClanChannel(ClanID.CLAN);
        ClanChannel gimChannel = client.getClanChannel(ClanID.GROUP_IRONMAN);
        ClanChannel guestClanChannel = client.getGuestClanChannel();
        String standardizedJoinedName = Text.standardize(clanMemberJoined.getClanMember().getName());

        ClanChannel clanChannelJoined = clanMemberJoined.getClanChannel();
        //If person joins clan/GIM chat, add to clan HashSet. PS switch does not like clanMemberJoined.getClanChannel()
        if (clanChannelJoined.equals(clanChannel)
                || clanChannelJoined.equals(gimChannel)) { //Alternatively use clanChannel != null && clanChannel.findMember(standardizedJoinedName) != null
            //findMember works both with .removeTags and with .standardize
            if (clanStandardizedUsernames.add(standardizedJoinedName)) {
                shouldRefreshChat = true;
            }
        }

        //If person joins guest CC chat, add to guest clan HashSet.
        if (clanChannelJoined.equals(guestClanChannel)) { //Alternatively use guestClanChannel != null && guestClanChannel.findMember(standardizedJoinedName) != null
            if (guestClanStandardizedUsernames.add(standardizedJoinedName)) {
                shouldRefreshChat = true;
            }
        }
    }

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
            System.out.println(this.chatTabAlerts.get(1).isFiltered());
            System.out.println(this.chatTabAlerts.get(1).isOwnMessage());
            System.out.println(this.chatTabAlerts.get(1).getChatTabNumber());
            System.out.println(this.chatTabAlerts.get(1).getVarcIntCountdownValue());
            System.out.println("total entries: "+this.chatTabAlerts.size());
        }
        if (commandExecuted.getCommand().equals("test3")) {
            System.out.println(publicFilterEnabled);
            System.out.println(configManager.getConfiguration(configGroup, "publicFilterEnabled"));
        }
        if (commandExecuted.getCommand().equals("test4")) {
            System.out.println(runelitePartyStandardizedUsernames);
            //raidPartyStandardizedUsernames.clear(); shouldRefreshChat = true;
        }
        if (commandExecuted.getCommand().equals("test5")) {
            clearRaidPartyHashset();
        }
    }

    //todo: Check how caching is implemented in the ChatFilterPlugin after you last worked on this plugin!
    @Subscribe
    public void onScriptCallbackEvent(ScriptCallbackEvent event) {
        //Use the RuneLite scriptcallback in ChatBuilder/ChatSplitBuilder.r2asm that ChatFilterPlugin also uses.
        //Does not affect overheads, only the chatbox
        if (!event.getEventName().equals("chatFilterCheck")) {
            return;
        }

        //Get the ChatMessageType
        int[] intStack = client.getIntStack();
        int intStackSize = client.getIntStackSize();
        final int messageType = intStack[intStackSize - 2];
        ChatMessageType chatMessageType = ChatMessageType.of(messageType);

        if (!isChatTabCustomFilterActiveChatMessageType(chatMessageType)) {
            //if chat is not filtered, return
            //shouldFilter already has a Strings.isNullOrEmpty(playerName) check
            return;
        }

        //Get the playerName
        final int messageId = intStack[intStackSize - 1];
        final MessageNode messageNode = client.getMessages().get(messageId);
        final String playerName = messageNode.getName();

        Set<ChatTabFilterOptions> chatTabSet = chatMessageTypeToChatTabFilterOptionsSet(chatMessageType);
        //ChatMessage that IS part of the publicChatFilterOptions set => needs to incorporate !publicChatFilterOptionsOH.contains in all of it (if in non-OH set => if not in overhead set => return false)
        //ChatMessage that is NOT part of the publicChatFilterOptions set => works perfectly with shouldFilterMessage
        boolean shouldFilter = chatTabSet == publicChatFilterOptions ? shouldFilterMessagePublicChatMessage(playerName) : shouldFilterMessage(chatTabSet, playerName);
        if (shouldFilter) {
            // Block the message
            System.out.println("Blocked message by " + Text.standardize(playerName)); //todo: remove
            intStack[intStackSize - 3] = 0;
        }
    }

    // Setting the priority is very important; otherwise it will race with other plugins such as probably core's ChatFilter and not hide the text!
    @Subscribe (priority = -10)
    public void onOverheadTextChanged(OverheadTextChanged overheadTextChanged) {
        //Overheads => the appropriate set is always publicChatFilterOptions set => works perfectly with shouldFilterMessage
        Actor actor = overheadTextChanged.getActor();
        if (!(actor instanceof Player) || !isChatTabCustomFilterActiveChatMessageType(ChatMessageType.PUBLICCHAT)) {
            //So e.g. Bob Barter (Herbs) at the GE doesn't get filtered
            //if chat is not filtered, return
            //shouldFilter already has a Strings.isNullOrEmpty(playerName) check
            return;
        }

        boolean shouldFilter = shouldFilterMessage(publicChatFilterOptions, actor.getName());
        if (shouldFilter) {
            actor.setOverheadText(" ");
            System.out.println(Text.standardize(actor.getName()) + " OH text filtered"); //todo: remove
        }
    }

    @Subscribe(priority = -2) //Run after ChatHistory plugin etc. Probably not necessary but can't hurt
    public void onMenuEntryAdded(MenuEntryAdded menuEntryAdded) {
        //Add right-click option(s) and potentially submenus to the chatstones
        if (menuEntryAdded.getType() != MenuAction.CC_OP.getId()) {
            return;
        }

        String menuEntryOption = menuEntryAdded.getOption();
        if (!menuEntryOption.contains("Show none")) {
            //return early if the menu entry option does not contain show none. ComponentID is checked a bit below via if (set == null)
            return;
        }

        //getActionParam1() seems to be getMenuEntry().getParam1() which seems to be getMenuEntry().getWidget().getId() = 10616843 = ComponentID (for public chat).
        int menuEntryAddedParam1 = menuEntryAdded.getActionParam1();
        Set<ChatTabFilterOptions> set = componentIDToChatTabFilterSet(menuEntryAddedParam1);
        Set<ChatTabFilterOptionsOH> setOH = componentIDToChatTabFilterSetOH(menuEntryAddedParam1);
        //Sets have been retrieved from the componentID. Returns null when componentID != chatstone componentID

        //Don't need to check SetOH, since it uses the same componentID as the normal set (both that of the public chat keystone)
        if (set == null) {
            //Not a chatstone componentID -> return
            return;
        }

        MenuEntry chatFilterEntry = null; //Used below to show a Change Sets Menu when the set is completely empty, if it should show something (based on shift-click settings etc.).
        int mainMenuIdx = -1; //Index for main menu to be used below with client.createMenuEntry(mainMenuIdx--)

        //Determine if the chatstone (e.g. private) should be filtered based on the componentID and the config set
        //shouldFilterChatType already has a ComponentID check build in that checks if it's a chatstone or not + checks if the filter option is enabled or not (if set is empty or not)
        if (shouldFilterChatType(menuEntryAddedParam1)) {
            //create MenuEntry and set its params
            chatFilterEntry = client.createMenuEntry(mainMenuIdx--)
                    .setType(MenuAction.RUNELITE)
                    .setParam1(menuEntryAddedParam1)
                    .onClick(e -> enableChatFilter(menuEntryAddedParam1));

            //Set name (option) of menuEntry
            final StringBuilder optionBuilder = new StringBuilder();
            //Pull tab name from menu since Trade/Group is variable + set Option based on config settings
            int colonIdx = menuEntryOption.indexOf(':');
            if (colonIdx != -1) {
                optionBuilder.append(menuEntryOption, 0, colonIdx).append(":</col> ");
            }
            optionBuilder.append("Show ");
            //At this point the </col> tag has been closed and "Show " has been added, e.g. "<col>Public:</col> Show "

            //Grab the abbreviations from the enum based on the selected config
            //Although maybe not the most optimal, I did not want to convert the HashSet to a List that I could order according to the enum at onConfigChanged
            //So I'm just looping over the enum values here
            for (ChatTabFilterOptions enumValue : ChatTabFilterOptions.values()) {
                if (set.contains(enumValue)) {
                    optionBuilder.append(enumValue.toAbbreviationString()).append("/"); //alternatively just use the getter
                }
            }
            //At this point the name/option is e.g. "<col>Public:</col> Show Friends/FC/CC/"
            menuEntryOption = optionBuilder.toString();

            //Replace entries with their OH equivalent if OH is added to the OH set
            //This is because I made the design decision that the chat filter (e.g. CC) needs to be enabled (visible) AND the OH chat filter (e.g. CC OH) needs to be active to only show CC OH
            //Order does not matter since I'm just replacing, so just iterate over the HashSet
            if (setOH != null) { //Already checks if componentID = public chat by returning null if it's not public.
                for (ChatTabFilterOptionsOH chatTabFilterOptionOH : setOH) {
                    menuEntryOption = menuEntryOption.replace(chatTabFilterOptionOH.toNonOHAbbreviationString() + "/", chatTabFilterOptionOH.toAbbreviationString() + "/"); //A slash is added, so it does not result in: "Public OH: Show Public OH/Friends/CC OH
                }
            }
            //At this point the name/option is e.g. "<col>Public:</col> Show Friends/FC/CC OH/"
            menuEntryOption = menuEntryOption.substring(0, menuEntryOption.length() - 1); //Remove the trailing "/". If deleted earlier, the final option is not properly replaced by its OH variant.
            chatFilterEntry.setOption(menuEntryOption);
            //At this point the name/option is e.g. "<col>Public:</col> Show Friends/FC/CC OH"

            //If the ClearRaidPartyMenu should be shown, based on the advanced setting and shift state & only add if that chat is currently filtered
            if (shouldShowShiftMenuSetting(clearRaidPartyShiftMenuSetting) && isChatFilteredComponentID(menuEntryAddedParam1)) { //If you also want to display this when the custom filter is not enabled but the show custom option is shown for the tab, remove " && isChatFilteredComponentID(menuEntryAddedParam1)"
                client.createMenuEntry(mainMenuIdx--)
                        .setType(MenuAction.RUNELITE)
                        .setParam1(menuEntryAddedParam1)
                        .onClick(this::clearRaidPartyHashsetManually)
                        .setOption("Clear Raid Party members");
            }
        }

        //Try to show a Change Sets Menu when the set is completely empty. Otherwise, add the submenu to chatFilterEntry! chatFilterEntry can get pretty long and it's difficult selecting the submenu otherwise due to the wide right click menu and the submenu usually showing up to the right.
        if (shouldShowShiftMenuSetting(changeChatSetsShiftMenuSetting)) { //If the ChangeSetsMenu should be shown, based on the advanced setting and shift state & only add if that chat is currently filtered
            //If the chat set is fully empty, add a parent menuentry!
            if (chatFilterEntry == null) { //aka if (!shouldFilterChatType(menuEntryAddedParam1))
                //Create parent menu
                chatFilterEntry = client.createMenuEntry(mainMenuIdx--)
                        .setParam1(menuEntryAddedParam1)
                        .setOption("Change Chat Sets");
            }
            //If the set with chats is not empty, we can change the menuEntry we added in the beginning (chatFilterEntry) into a RUNELITE_SUBMENU
            //If the set is empty, we just created it and still need to set the type.
            chatFilterEntry.setType(MenuAction.RUNELITE_SUBMENU);

            //Add the submenus, use enum.values() to loop over the values.
            int submenuIdx = -1;
            for (ChatTabFilterOptions chatTabFilterOption : ChatTabFilterOptions.values()) {
                StringBuilder optionBuilder = getSubMenuStringBuilder(set.contains(chatTabFilterOption), chatTabFilterOption.toString());
                client.createMenuEntry(submenuIdx--)
                        .setType(MenuAction.RUNELITE)
                        .setParent(chatFilterEntry)
                        .setOption(optionBuilder.toString())
                        .onClick(e -> addRemoveValueFromChatSet(set, chatTabFilterOption, menuEntryAddedParam1)); //Adds or removes to/from the set, based on if the value is already in the set or not.
            }

            if (setOH != null) { //Already checks if componentID = public chat (it's null if it's not public)
                submenuIdx = -1;
                for (ChatTabFilterOptionsOH chatTabFilterOptionOH : ChatTabFilterOptionsOH.values()) {
                    StringBuilder optionBuilder = getSubMenuStringBuilder(setOH.contains(chatTabFilterOptionOH), chatTabFilterOptionOH.toString());
                    client.createMenuEntry(submenuIdx--)
                            .setType(MenuAction.RUNELITE)
                            .setParent(chatFilterEntry)
                            .setOption(optionBuilder.toString())
                            .onClick(e -> addRemoveValueFromChatSetOH(setOH, chatTabFilterOptionOH)); //Adds or removes to/from the set, based on if the value is already in the set or not.
                }
            }
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked menuOptionClicked) {
        final int menuOptionClickedParam1 = menuOptionClicked.getParam1();
        String menuOption = menuOptionClicked.getMenuOption();
        //The menu option for show friends is "<col=ffff00>Public:</col> Show friends"
        //First check if it's a valid chatfilter option, then check that it's not the one we inserted => disable chat filtering if user clicked e.g. Public: Show friends
        //Since this also contains stuff like "Switch tab", "Clear history" etc. which should not turn off the custom filter, we also check for "Show" and if it does not contain some text from a menu entry we added ourselves.
        if (isComponentIDChatStone(menuOptionClickedParam1) && menuOption.contains("Show")) { //alternatively you could use menuOption.contains("<col=ffff00>") && menuOption.contains("Show") ?
            //Remove everything before the : so it doesn't match <col=ffff00>Public:</col>
            int idx = menuOption.indexOf(':');
            if (idx != -1) {
                menuOption = menuOption.substring(idx);
            }
            for (ChatTabFilterOptions enumValue : ChatTabFilterOptions.values()) { //All the abbreviations from ChatTabFilterOptionsOH contain the non-OH abbreviation so contains still matches
                if (menuOption.contains(enumValue.toAbbreviationString())) { //Plugin uses Friends instead of friends (osrs game)
                    //return in case it is the menu entry/option we added ourselves! We do not want to turn off the filter when clicking on our menu entry/option.
                    return;
                }
            }
            //If the specific chat is filtered, disable the filter. Technically the if statement could potentially be skipped.
            if (isChatFilteredComponentID(menuOptionClickedParam1)) {
                setChatFilterConfig(menuOptionClickedParam1, false);
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        if (setChatsToPublicFlag) {
            executeSetChatsToPublic();
            setChatStoneWidgetTextAll(); //Also executed in setChatsToPublic() to improve the feeling (makes it feel snappier)
            setChatsToPublicFlag = false;
            System.out.println("size (supposed to be 0) = "+this.chatTabAlerts.size()); //todo: remove
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

        if (shouldRefreshChat) {
            //Refresh chat in case someone got added to the list. Using a flag and doing it onGameTick so it doesn't potentially proc multiple time per gameTick, e.g. when joining an FC/CC
            client.refreshChat();
            shouldRefreshChat = false; //Reset flag
        }
    }

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
                //Procs once per tick, also procs inside ToB. However, then S28.5 TOB_PARTY_INTERFACE and S28.12 (client.getWidget(InterfaceID.TOB, TOB_PARTY_INTERFACE_NAMES_CHILDID)) are hidden
                processToBPartyInterface();
                break;
            case TOA_PARTYDETAILS_BACK_BUTTON_SCRIPTID:
                //First 6615 [clientscript,toa_partydetails_init] is run to probably initialize it. Then 6722 [clientscript,toa_partydetails_addmember] runs 8 times to add one member each. Then 6761 [proc,toa_partydetails_sortbutton_draw] runs 10 times. Finally, 6765 [proc,toa_partydetails_back_button], 6756 [proc,toa_partydetails_summary], and 6770 [proc,toa_invocations_side_panel_update] proc once. All in the same gamecycle.
                processToABoard();
                break;
            case CHAT_ALERT_ENABLE_SCRIPTID:
                if (this.chatTabAlerts.size() > 0) {
                    this.chatTabAlerts.remove(0);
                }
                //todo: add code with switch based on currentChatTabAlertTab, has to take the advanced config boolean into account, has to take the filterTriggered boolean into account and has to take the own name thingy and it's advanced config setting into account!
                break;
        }
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired scriptPreFired) {
        if (scriptPreFired.getScriptId() == CHAT_ALERT_ENABLE_SCRIPTID) {
            //Set the VarcInt that determines flashing/solid/no chat alert before it goes off. This way you can potentially set it to this value in PostScriptFired if a chat message was filtered.
            int currentChatTabAlertTab = client.getIntStack()[0]; //1 = game. 2 = public. 3 = friends but does not show up when private is split (which is good, because the tab does also not flash then!). 4 = fc. 5 = cc. 6 = trade.
            switch (currentChatTabAlertTab) {
                case 2:
                    //publicVarcIntCountdownValue = client.getVarcIntValue(PUBLIC_VARC_INT_COUNTDOWN_ID);
                    break;
                case 3:
                    //privateVarcIntCountdownValue = client.getVarcIntValue(PRIVATE_VARC_INT_COUNTDOWN_ID);
                    break;
                case 4:
                    //fcVarcIntCountdownValue = client.getVarcIntValue(FC_VARC_INT_COUNTDOWN_ID);
                    break;
                case 5:
                    //ccVarcIntCountdownValue = client.getVarcIntValue(CC_VARC_INT_COUNTDOWN_ID);
                    break;
                case 6:
                    //tradeVarcIntCountdownValue = client.getVarcIntValue(TRADE_VARC_INT_COUNTDOWN_ID); //todo: fix this
                    break;
            }
            if (this.chatTabAlerts.size() > 0) {
                if (this.chatTabAlerts.get(0).getChatTabNumber() == currentChatTabAlertTab) {
                    System.out.println("this.chatTabAlerts.get(0).getChatTabNumber() == currentChatTabAlertTab"); //todo: remove
                } else {
                    System.out.println("NUMBER DOES NOT MATCH: "+this.chatTabAlerts.get(0).getChatTabNumber()+" vs currentChatTabAlertTab: "+currentChatTabAlertTab);
                } //todo: see stuff calendar!
            }
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        //The order is: ChatMessage => ScriptPreFired 180 => ScriptPostFired 180. However, sometimes it might be CM => CM => PreF => PostF => PreF => PostF. See "docs/testing/ChatMessage ScriptPrePostFired" for more info.
        ChatMessageType chatMessageType = chatMessage.getType();
        if (!isChatTabCustomFilterActiveChatMessageType(chatMessageType)) {
            //if chat is not filtered, return
            //shouldFilter already has a Strings.isNullOrEmpty(playerName) check
            return;
        }

        String playerName = chatMessage.getName();
        Set<ChatTabFilterOptions> chatTabSet = chatMessageTypeToChatTabFilterOptionsSet(chatMessageType);
        boolean shouldFilter = chatTabSet == publicChatFilterOptions ? shouldFilterMessagePublicChatMessage(playerName) : shouldFilterMessage(chatTabSet, playerName);
        boolean ownMessage = playerName.equals(client.getLocalPlayer().getName());

        //todo: potentially revert this to the old approach, not using a custom class
        ChatTabAlert alert = new ChatTabAlert(shouldFilter, ownMessage);
        switch (chatMessageType) {
            //AUTOTYPER	is filtered on public = on anyway
            case PUBLICCHAT:
            case MODCHAT:
                alert.setChatTabNumber(2);
                break;
            case PRIVATECHAT:
            case MODPRIVATECHAT:
                alert.setChatTabNumber(3);
                break;
            case FRIENDSCHAT:
                alert.setChatTabNumber(4);
                break;
            case CLAN_CHAT:
            case CLAN_GIM_CHAT:
            case CLAN_GUEST_CHAT:
                alert.setChatTabNumber(5);
                break;
            case TRADEREQ:
                //TRADE and TRADE_SENT are not received when someone tries to trade you, only TRADEREQ
                alert.setChatTabNumber(6);
                break;
        }

        this.chatTabAlerts.add(alert);

        //todo: check if this also works for the chatfilter plugin with nothing filtered here (so if this fixes both chatfilter and chat filter extended).
        //todo: If so, either make it an advanced config option to only work when the chat is filtered by chat filter extended (via dropdown: always, only chat filter extended messages, never)
        //todo: and also edit the advanced config descriptions of the already existing options + the readme
    }




    //todo: check why adding e.g. friends to an empty private set, then enabling it nukes the set... nvm, changing antyhing here doesnt seem to work?

    @Subscribe
    public void onVarbitChanged(VarbitChanged varbitChanged) {
        //For some reason the channel and clan tab also have Varbits attached to them, while public, private, and trade do not (see script 184/185).
        //The varbit just sometimes gets transmitted, e.g. when leaving ToB, when hopping worlds, or when changing e.g. the FC filter (this also transmits the CC varbit).
        //Script 152 or 184 does not run then, so no use doing onScriptPrefired and then using Client.setVarbit
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
        if (!partyService.isInParty()) { //If user left the party
            if (clearRLPartySetLeave) {
                runelitePartyStandardizedUsernames.clear();
                shouldRefreshChat = true;
            }
        }
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
            if (runelitePartyStandardizedUsernames.add(standardizedUsername)) {
                shouldRefreshChat = true;
            }
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
        if (inCoXRaidOrLobby //If Varbits.IN_RAID > 0
                && raidPartyStandardizedUsernames.add(Text.standardize(playerSpawned.getPlayer().getName()))) { //Standardize playername that joined cox lobby / cox raid and add to hashset.
            shouldRefreshChat = true;
        }
    }

    private void convertCommaSeparatedConfigStringToSet(String configString, HashSet<String> setToConvertTo) {
        //Convert a CSV config string to a set
        setToConvertTo.clear();
        setToConvertTo.addAll(Text.fromCSV(Text.standardize(configString)));
    }

    private void getCoXVarbit() {
        if (client.getGameState() == GameState.LOGGED_IN || client.getGameState() == GameState.LOADING) {
            inCoXRaidOrLobby = client.getVarbitValue(Varbits.IN_RAID) > 0; //Convert the int to boolean. 0 = false, 1 = true.
        }
    }

    private void getCoXPlayers() {
        //Get all players when inside CoX lobby or CoX raid and add them to the hashset after standardizing the name
        //Useful when starting the plugin inside CoX or when clearing the raid party inside CoX (doesn't proc PlayerSpawned)
        if (!inCoXRaidOrLobby ||
                (client.getGameState() != GameState.LOGGED_IN
                        && client.getGameState() != GameState.LOADING)) {
            //If Varbits.IN_RAID == 0 or wrong gamestate, return early
            return;
        }

        for (Player player : client.getPlayers()) { //In case this actually becomes a thing with boats, probably replace with something like client.getTopLevelWorldView().players().stream().collect(Collectors.toCollection(ArrayList::new)). Maybe gotta check if the wv is null though.
            if (raidPartyStandardizedUsernames.add(Text.standardize(player.getName()))) {
                shouldRefreshChat = true;
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
        if (client.getVarpValue(IN_A_RAID_VARPID) > 0 && client.getVarpValue(TOA_PARTY_VARPID) > -1) {
            for (int i = 0; i < 8; i++) {
                addInRaidUsernamesVarClientStr(TOA_IN_RAID_VARCSTR_PLAYER1_INDEX + i);
            }
        }
    }

    private void getFCMembers() {
        //To add all the FC members to the hashset. Useful if the plugin gets enabled while already in an FC, so should run in StartUp.
        FriendsChatManager friendsChatManager = client.getFriendsChatManager();
        if (friendsChatManager == null) {
            return;
        }

        for (FriendsChatMember member : friendsChatManager.getMembers()) {
            if (channelStandardizedUsernames.add(Text.standardize(member.getName()))) {
                shouldRefreshChat = true;
            }
        }
        //Could technically collapse this with e.g. a forEach() stream like below, but readability is meh then IMO
        //Arrays.stream(friendsChatManager.getMembers()).filter(member -> channelStandardizedUsernames.add(Text.standardize(member.getName()))).forEach(member -> shouldRefreshChat = true);
    }

    private void getCCMembers() {
        //To add all the CC/GIM members to the hashset. Useful if the plugin gets enabled while already in a CC, so should run in StartUp.
        //Add own CC
        ClanChannel clanChannel = client.getClanChannel();
        ClanSettings clanSettings = client.getClanSettings(ClanID.CLAN);
        addClanMembers(clanChannel, clanSettings, clanStandardizedUsernames);

        //Add GIM
        ClanChannel gimClanChannel = client.getClanChannel(ClanID.GROUP_IRONMAN);
        ClanSettings gimSettings = client.getClanSettings(ClanID.GROUP_IRONMAN);
        addClanMembers(gimClanChannel, gimSettings, clanStandardizedUsernames);
        System.out.println(clanStandardizedUsernames); //todo: remove
    }

    private void addClanMembers(ClanChannel clanChannel, ClanSettings clanSettings, HashSet<String> clanHashSet) {
        if (clanSettings != null) {
            //Adds all the members to the HashSet (according to the clan settings)
            for (ClanMember clanMember : clanSettings.getMembers()) {
                if (clanHashSet.add(Text.standardize(clanMember.getName()))) {
                    shouldRefreshChat = true;
                }
            }
        }

        //Clan members get added via the clan settings, but clan/guest clan guests are not a part of those.
        if (clanChannel != null) {
            //Previous solution does not add the guests that are already in the CC, also add those
            for (ClanChannelMember clanMember : clanChannel.getMembers()) {
                if (clanHashSet.add(Text.standardize(clanMember.getName()))) {
                    shouldRefreshChat = true;
                }
            }
        }
    }

    private void getGuestCCMembers() {
        //To add all the guest CC members to the hashset. Useful if the plugin gets enabled while already in a guest CC, so should run in StartUp.
        //Add guest CC
        ClanChannel guestClanChannel = client.getGuestClanChannel();
        ClanSettings guestClanSettings = client.getGuestClanSettings();
        addClanMembers(guestClanChannel, guestClanSettings, guestClanStandardizedUsernames);
        System.out.println(guestClanStandardizedUsernames); //todo: remove
    }

    @Nullable
    private Set<ChatTabFilterOptions> componentIDToChatTabFilterSet(int componentID) {
        //Returns the Set<ChatTabFilterOptions> based on the componentID. Originally had it in an Object with also OH, but it's kind of annoying to use so screw that.
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
    private Set<ChatTabFilterOptionsOH> componentIDToChatTabFilterSetOH(int componentID) {
        //Returns the Set<ChatTabFilterOptionsOH> based on the componentID. Originally had it in an Object with also 3D/regular, but it's kind of annoying to use so screw that.
        //Returns null when componentID != public chatstone componentID
        if (componentID == ComponentID.CHATBOX_TAB_PUBLIC) {
            return publicChatFilterOptionsOH;
        }
        return null;
    }

    private boolean shouldFilterChatType(int componentID) {
        //Should a chatstone (e.g. private) be filtered based on the componentID and the config set
        //Does not check if the filter is currently active (i.e. if it's on custom)
        Set<ChatTabFilterOptions> set = componentIDToChatTabFilterSet(componentID);
        //componentIDToChatTabFilterSet already checks the componentID, so we don't have to check if it's a chatstone componentID besides doing a null check
        //The publicOH filter only works when the normal one is also active, so can ignore the OH one for now.
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
        shouldRefreshChat = true;
    }

    private void clearRaidPartyHashsetManually(MenuEntry menuEntry) {
        clearRaidPartyHashset();
        //Rebuild the raid party hashset by adding the current people to it. Events that run every gametick (either via onGameTick or e.g. via a script that runs every gametick, are excluded here since they'll run anyway).
        //Thus, CoX bank is excluded, ToB/ToA lobby party interface is excluded.
        getCoXPlayers(); //Get CoX players because it does not trigger onPlayerSpawned while inside a raid.
        processToBBoard(); //Person might close the interface before the script procs.
        processToABoard(); //Person might close the interface before the script procs.
        getToBPlayers(); //Checks if player is inside ToB to only add them then. Use addAllInRaidUsernamesVarClientStr() if you also want to add when outside ToB or old ToA players
        getToAPlayers(); //Checks if player is inside ToA to only add them then. Use addAllInRaidUsernamesVarClientStr() if you also want to add when outside ToA or old ToB players
        client.refreshChat(); //Refresh chat after manually changing the raid filter set
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Chat Filter Extended: The Raid Party members set has been cleared.", "");
    }

    private boolean isComponentIDChatStone(int componentID) {
        return chatboxComponentIDs.contains(componentID);
    }

    private void enableChatFilter(int componentID) {
        //Enables the chat filter for the specific tab is the users selects the appropriate menu option.
        setChatFilterConfig(componentID, true);
        setChatsToPublic();
    }

    private void setChatFilterConfig(int componentID, boolean enableFilter) {
        //Set the RL config value for a chat: is the filter enabled or disabled?
        if (isComponentIDChatStone(componentID)) {
            executeSetChatFilterConfig(componentID, enableFilter);
        }
    }

    private void executeSetChatFilterConfig(int componentID, boolean enableFilter) {
        //Separate method, so it can be easily run by putting in the componentID instead having to enter a MenuEntry
        //publicFilterEnabled = enableFilter is not necessary since ConfigManager does trigger updateConfig() if the config value actually gets changed from false to true or vice versa
        //Alternatively use a switch (componentID) statement like you did before. It's probably more efficient execution wise, but we got these lists anyway and this is more compact
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
        //This code is being executed in onGameTick since otherwise some plugins like SCIC might bug out. See SCIC errors in testing for more info.

        //Public, private, trade remember between hops => they are only set when onlyVolatile is set to false.
        //Channel and clan are probs varbit based => they are always set when this method is executed ("volatile").
        //Channel, Clan don't remember between hopping; potentially related to varbits as described here https://discord.com/channels/301497432909414422/301497432909414422/1086022946633547867 (i.e. I suspect when hopping it reads the state from the varbits and then sets the chat filters according to those values)
        clientThread.invoke(() -> {
            //Could potentially put this nicely in an enum at some point. Should probably also document what other arguments do then. 1 might be game, but it's not listed in script 184 proc,chat_set_filter. Regarding 2nd argument it's just always +1 to get the next MenuOption and if you enter a value that's too high, it goes to show all/on (for some chat tabs?)
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
            chatWidget.getStaticChildren()[2].setText(TAB_CUSTOM_TEXT_STRING); //or e.g. chatWidget.getStaticChildren().length-1 but that might change more often idk
        }
    }

    private boolean isChatFilteredComponentID(int componentID) {
        //Returns true if the chat is filtered based on ComponentId.
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

    //Get the StringBuilder for the submenus.
    //boolean should be set.contains(chatTabFilterOption) or setOH.contains(chatTabFilterOption)
    //String should be chatTabFilterOption.toString() or chatTabFilterOptionOH.toString()
    private StringBuilder getSubMenuStringBuilder(boolean setContainsChatTabFilterOption, String chatTabFilterOption) {
        StringBuilder optionBuilder = new StringBuilder();
        if (setContainsChatTabFilterOption) { //Already in the set, so the option should be "Remove "
            optionBuilder.append("Remove ");
        } else {
            optionBuilder.append("Add "); //chatTabFilterOption is not in the active Set<ChatTabFilterOptions> set yet, so option should start with "Add "
        }
        optionBuilder.append(chatTabFilterOption); //Add the value to the OptionBuilder so e.g. "Add Friends"
        return optionBuilder;
    }

    private void addRemoveValueFromChatSet(Set<ChatTabFilterOptions> chatSet, ChatTabFilterOptions filterOption, int componentID) {
        //Add or remove a value from a chat set based on if the set already contains the value or not.
        //Used in the right click menu to add or remove a value from the set.
        if (!chatSet.add(filterOption)) {
            //If the set does not contain the value, add it, return !true aka false, so don't remove it.
            //If the set does contain the value, it can't be added and the if statement is !false aka true, so it'll remove the value.
            chatSet.remove(filterOption);
        }

        //Get the config key name based on the componentID
        String keyName = componentIDToChatTabFilterKeyName(componentID);
        if (keyName == null) {
            //Returns null when componentID != chatstone componentID
            return;
        }

        configManager.setConfiguration(configGroup, keyName, chatSet);
        //Potentially add a chat message when changing the chatSet, but might get too spammy when adding/removing multiple values. One can already confirm it happened by just right-clicking on the chat tab and seeing "Show: Public/Friends/FC/CC" etc.
        if (keyName.equals("privateChatFilterOptions") && !forcePrivateOn) {
            //Notification when people screw with the private filter without forcePrivateOn so they don't complain about it not working properly.
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Chat Filter Extended: Private filtering generally only works well when 'force private to on' is enabled in the plugin's config settings.", "");
        }
    }

    @Nullable
    private String componentIDToChatTabFilterKeyName(int componentID) {
        //Returns the ChatFilterOptions keyname based on the componentID, because reflection bad so can't get the name that way.
        //Returns null when componentID != chatstone componentID
        //Specifically opted to use a Switch instead of a for loop+list combo because the performance is a bit better. In the end both approaches are meh and don't differ that much in performance probs.
        switch (componentID) {
            case ComponentID.CHATBOX_TAB_PUBLIC:
                return "publicChatFilterOptions";
            case ComponentID.CHATBOX_TAB_PRIVATE:
                return "privateChatFilterOptions";
            case ComponentID.CHATBOX_TAB_CHANNEL:
                return "channelChatFilterOptions";
            case ComponentID.CHATBOX_TAB_CLAN:
                return "clanChatFilterOptions";
            case ComponentID.CHATBOX_TAB_TRADE:
                return "tradeChatFilterOptions";
        }
        return null;
    }

    private void addRemoveValueFromChatSetOH(Set<ChatTabFilterOptionsOH> chatSet, ChatTabFilterOptionsOH filterOption) {
        //Add or remove a value from a chat set based on if the set already contains the value or not.
        //Used in the right click menu to add or remove a value from the set.
        if (!publicChatFilterOptionsOH.add(filterOption)) {
            //If the set does not contain the value, add it, return !true aka false, so don't remove it.
            //If the set does contain the value, it can't be added and the if statement is !false aka true, so it'll remove the value.
            publicChatFilterOptionsOH.remove(filterOption);
        }
        configManager.setConfiguration(configGroup, "publicChatFilterOptionsOH", chatSet);
    }

    private boolean shouldFilterMessagePublicChatMessage(String playerName) {
        //Should the message be filtered, only for onChatMessage (ScriptCallback) and the public set, based on the sender's name.
        //For the rest, see shouldFilterMessage
        Set<ChatTabFilterOptions> chatTabHashSet = publicChatFilterOptions;
        Set<ChatTabFilterOptionsOH> chatTabHashSetOH = publicChatFilterOptionsOH;
        if (chatTabHashSet == null || chatTabHashSet.isEmpty()) { //Custom is not meant to be on in this case anyway, or the type does not correspond with a ChatMessageType we know.
            return false;
        }

        //todo: add map as alternative code (idk if there is an alternative code thing for this though, then just make a note). Make code all || probably, potentially with method.

        playerName = Text.standardize(playerName); //Very likely works considering other methods work with a standardized name. Can't test this though since my name doesn't have e.g. a space.
        if (Strings.isNullOrEmpty(playerName)) {
            return false;
        }
        //If it's your own message, don't filter
        if (playerName.equals(Text.standardize(client.getLocalPlayer().getName()))) {
            return false;
        }
        if (chatTabHashSet.contains(ChatTabFilterOptions.FRIENDS) && !chatTabHashSetOH.contains(ChatTabFilterOptionsOH.FRIENDS) && client.isFriended(playerName, false)) {
            return false;
        }
        if (chatTabHashSet.contains(ChatTabFilterOptions.FC) && !chatTabHashSetOH.contains(ChatTabFilterOptionsOH.FC) && channelStandardizedUsernames.contains(playerName)) {
            return false;
        }
        if (chatTabHashSet.contains(ChatTabFilterOptions.CC) && !chatTabHashSetOH.contains(ChatTabFilterOptionsOH.CC) && clanStandardizedUsernames.contains(playerName)) {
            return false;
        }
        if (chatTabHashSet.contains(ChatTabFilterOptions.GUEST_CC) && !chatTabHashSetOH.contains(ChatTabFilterOptionsOH.GUEST_CC) && guestClanStandardizedUsernames.contains(playerName)) {
            return false;
        }
        if (chatTabHashSet.contains(ChatTabFilterOptions.PARTY) && !chatTabHashSetOH.contains(ChatTabFilterOptionsOH.PARTY) && runelitePartyStandardizedUsernames.contains(playerName)) {
            return false;
        }
        if (chatTabHashSet.contains(ChatTabFilterOptions.RAID) && !chatTabHashSetOH.contains(ChatTabFilterOptionsOH.RAID) && raidPartyStandardizedUsernames.contains(playerName)) {
            return false;
        }
        //Get appropriate whitelist and if enabled, check if this whitelist contains the playername
        Set<String> whitelist = chatTabFilterOptionsSetToWhitelist(chatTabHashSet);
        if (chatTabHashSet.contains(ChatTabFilterOptions.WHITELIST)
                && !chatTabHashSetOH.contains(ChatTabFilterOptionsOH.WHITELIST)
                && whitelist != null
                && whitelist.contains(playerName)) {
            return false;
        }

        //Public = everyone that did not fit in the earlier groups: not friend, not FC/CC/Guest CC/Raid party/RL party member and not on the appropriate whitelist
        //Thus, public = the randoms
        //It's not the local player, so don't have to check for that.
        //noinspection RedundantIfStatement
        if (chatTabHashSet.contains(ChatTabFilterOptions.PUBLIC) //If statement can be simplified, but specifically opted not to do this to increase readability.
                && !chatTabHashSetOH.contains(ChatTabFilterOptionsOH.PUBLIC)
                && !client.isFriended(playerName, false)
                && !channelStandardizedUsernames.contains(playerName)
                && !clanStandardizedUsernames.contains(playerName)
                && !guestClanStandardizedUsernames.contains(playerName)
                && !runelitePartyStandardizedUsernames.contains(playerName)
                && !raidPartyStandardizedUsernames.contains(playerName)
                && (whitelist != null && !whitelist.contains(playerName))) {
            //todo: maybe use booleans instead of checking again if hashset contains ^
            return false;
        }
        return true;
    }

    private boolean shouldFilterMessage(Set<ChatTabFilterOptions> chatTabHashSet, String playerName) {
        //Should the message be filtered, based on ChatTabFilterOptions and the sender's name.
        //For public onChatMessage (ScriptCallback), check shouldFilterMessagePublicChatMessage!
        if (chatTabHashSet == null || chatTabHashSet.isEmpty()) { //Custom is not meant to be on in this case anyway, or the type does not correspond with a ChatMessageType we know.
            return false;
        }

        //todo: add map as alternative code. Make code all || probably, potentially with method.

        playerName = Text.standardize(playerName); //Very likely works considering other methods work with a standardized name. Can't test this though since my name doesn't have e.g. a space.
        if (Strings.isNullOrEmpty(playerName)) {
            return false;
        }
        //If it's your own message, don't filter
        if (playerName.equals(Text.standardize(client.getLocalPlayer().getName()))) {
            return false;
        }
        if (chatTabHashSet.contains(ChatTabFilterOptions.FRIENDS) && client.isFriended(playerName, false)) {
            return false;
        }
        if (chatTabHashSet.contains(ChatTabFilterOptions.FC) && channelStandardizedUsernames.contains(playerName)) {
            return false;
        }
        if (chatTabHashSet.contains(ChatTabFilterOptions.CC) && clanStandardizedUsernames.contains(playerName)) {
            return false;
        }
        if (chatTabHashSet.contains(ChatTabFilterOptions.GUEST_CC) && guestClanStandardizedUsernames.contains(playerName)) {
            return false;
        }
        if (chatTabHashSet.contains(ChatTabFilterOptions.PARTY) && runelitePartyStandardizedUsernames.contains(playerName)) {
            return false;
        }
        if (chatTabHashSet.contains(ChatTabFilterOptions.RAID) && raidPartyStandardizedUsernames.contains(playerName)) {
            return false;
        }
        //Alternatively do something like this, but kinda meh solution. Different solution would be better.
		/*
		List<ChatTabFilterOptions> ChatTabFilterOptionsList = ImmutableList.of(ChatTabFilterOptions.FC, ChatTabFilterOptions.CC, ChatTabFilterOptions.GUEST_CC, ChatTabFilterOptions.PARTY, ChatTabFilterOptions.RAID);
		List<HashSet<String>> standardizedUsernamesHashSetList = Arrays.asList(channelStandardizedUsernames, clanStandardizedUsernames, guestClanStandardizedUsernames, runelitePartyStandardizedUsernames, raidPartyStandardizedUsernames);
		for (int i = 0; i < ChatTabFilterOptionsList.size(); i++) {
			if (chatTabHashSet.contains(ChatTabFilterOptionsList.get(i)) && standardizedUsernamesHashSetList.get(i).contains(playerName)) {
				return false;
			}
		}
		*/
        //Get appropriate whitelist and if enabled, check if this whitelist contains the playername
        Set<String> whitelist = chatTabFilterOptionsSetToWhitelist(chatTabHashSet);
        if (chatTabHashSet.contains(ChatTabFilterOptions.WHITELIST) && whitelist != null && whitelist.contains(playerName)) {
            return false;
        }

        //Public = everyone that did not fit in the earlier groups: not friend, not FC/CC/Guest CC/Raid party/RL party member and not on the appropriate whitelist
        //Thus, public = the randoms
        //It's not the local player, so don't have to check for that.
        //noinspection RedundantIfStatement
        if (chatTabHashSet.contains(ChatTabFilterOptions.PUBLIC) //If statement can be simplified, but specifically opted not to do this to increase readability.
                && !client.isFriended(playerName, false)
                && !channelStandardizedUsernames.contains(playerName)
                && !clanStandardizedUsernames.contains(playerName)
                && !guestClanStandardizedUsernames.contains(playerName)
                && !runelitePartyStandardizedUsernames.contains(playerName)
                && !raidPartyStandardizedUsernames.contains(playerName)
                && (whitelist != null && !whitelist.contains(playerName))) {
            return false;
        }
        //todo: maybe use booleans instead of checking again if hashset contains ^

        //Alternatively do something like this, but I don't really like this solution.
		/*
		if (chatTabHashSet.contains(ChatTabFilterOptions.PUBLIC)) {
			if (client.isFriended(playerName, false) || (whitelist != null && whitelist.contains(playerName))) {
				return false;
			}
			for (HashSet<String> set : standardizedUsernamesHashSetList) {
				if (set.contains(playerName)) {
					return false;
				}
			}
		}
		*/
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

		However, I'd like the usernames to persist until the user logs out or leaves the chat (if configured in advanced settings), since sometimes people briefly leave the FC/CC/guest CC and still type etc
		Thus, I've specifically opted to not use this.
		 */

    @SuppressWarnings("BooleanMethodIsAlwaysInverted") //This is true, but I like keeping it like this for my own logic
    private boolean isChatTabCustomFilterActiveChatMessageType(ChatMessageType chatMessageType) {
        //Returns true if the chat tab is set to Show: custom, based on the ChatMessageType
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
                    //TRADE and TRADE_SENT are not received when someone tries to trade you, only TRADEREQ
                    return tradeFilterEnabled;
            }
        }
        return false;
    }

    @Nullable
    private Set<ChatTabFilterOptions> chatMessageTypeToChatTabFilterOptionsSet(ChatMessageType chatMessageType) {
        //Translates the ChatMessageType to the appropriate hashset (not OH, so not for overheads).
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

    @Nullable
    private Set<String> chatTabFilterOptionsSetToWhitelist(Set<ChatTabFilterOptions> chatTabFilterOptionsSet) {
        //Translate the ChatTabFilterOptionsSet to the whitelist.
        //Switch statement is not compatible with this type, so if statements it is.
        //todo: check what this method exactly does
        if (chatTabFilterOptionsSet != null) {
            if (chatTabFilterOptionsSet.equals(publicChatFilterOptions)) {
                return publicWhitelist;
            }
            if (chatTabFilterOptionsSet.equals(privateChatFilterOptions)) {
                return privateWhitelist;
            }
            if (chatTabFilterOptionsSet.equals(channelChatFilterOptions)) {
                return channelWhitelist;
            }
            if (chatTabFilterOptionsSet.equals(clanChatFilterOptions)) {
                return clanWhitelist;
            }
            if (chatTabFilterOptionsSet.equals(tradeChatFilterOptions)) {
                return tradeWhitelist;
            }
        }
        return null;
    }

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
        //Process the ToB board
        Widget topHalfToBBoardWidget = client.getWidget(TOB_BOARD_ID, TOP_HALF_TOB_BOARD_CHILDID);
        Widget bottomHalfToBBoardWidget = client.getWidget(TOB_BOARD_ID, BOTTOM_HALF_TOB_BOARD_CHILDID);
        processBoard(topHalfToBBoardWidget, bottomHalfToBBoardWidget);
    }

    private void processBoard(Widget topOrMembersPart, Widget bottomOrApplicantsPart) {
        //Since processing the ToB and ToA boards works the same, this method works for both.
        //Please refer to processToBBoard and processToABoard for more info.
        HashSet<String> raidPartyStandardizedUsernamesTemp = new HashSet<>();
        if (topOrMembersPart != null && topOrMembersPart.getDynamicChildren() != null) {
            for (int i = 0; i < topOrMembersPart.getDynamicChildren().length; i++) {
                //Get child that has type 3 => next one has to be the username
                if (Objects.requireNonNull(topOrMembersPart.getChild(i)).getType() == 3) {
                    //Index of the one that has name is type 3 index + 1
                    Widget nameWidget = topOrMembersPart.getChild(i + 1);
                    if (nameWidget != null && nameWidget.getType() == 4) {
                        //If right type (4), get the text and standardize it
                        String standardizedRaidUsername = Text.standardize(nameWidget.getText()); //Also removes the leading and trailing spaces from -
                        if (!standardizedRaidUsername.equals("-")) { //Skip empty entries and add to temporary HashSet to remember
                            raidPartyStandardizedUsernamesTemp.add(standardizedRaidUsername); //Return value does not have to be checked as this is the Temp set!
                        }
                    }
                }
            }
        }
        if (bottomOrApplicantsPart != null && bottomOrApplicantsPart.getDynamicChildren() != null) {
            for (int i = 0; i < bottomOrApplicantsPart.getDynamicChildren().length; i++) {
                //Get child that has type 3 => next one has to be username
                if (Objects.requireNonNull(bottomOrApplicantsPart.getChild(i)).getType() == 3) {
                    //Index of the one that has name is type 3 index + 1
                    Widget nameWidget = bottomOrApplicantsPart.getChild(i + 1);
                    if (nameWidget != null && nameWidget.getType() == 4) {
                        //If right type (4), get the text and standardize it, then add it to the temp hashset
                        //Skipping empty entries ("-") is not required since they are not added to the bottom half of the board (those dynamic children just don't exist).
                        raidPartyStandardizedUsernamesTemp.add(Text.standardize(nameWidget.getText())); //Return value does not have to be checked as this is the Temp set.
                    }
                }
            }
        }
        //If it's the user's party/the user applied, add the temporary HashSet to the real HashSet
        if (client.getLocalPlayer() != null
                && raidPartyStandardizedUsernamesTemp.contains(Text.standardize(client.getLocalPlayer().getName()))
                //The addAll() method returns true if the collection changes as a result of elements being added into it; otherwise, it will return false
                && raidPartyStandardizedUsernames.addAll(raidPartyStandardizedUsernamesTemp)) {
            shouldRefreshChat = true;
        }
    }

    //private static final int TOB_PARTY_INTERFACE_NAMES_CHILDID = 12; //S28.12
    //No party text = -<br>-<br>-<br>-<br>-
    //Party text = Username<br>Username2<br>-<br>-<br>-
    private void processToBPartyInterface() {
        //Processed the party interface in the ToB lobby
        Widget tobPartyInterfaceNamesWidget = client.getWidget(InterfaceID.TOB, TOB_PARTY_INTERFACE_NAMES_CHILDID); //S28.12
        processRaidPartyInterface(tobPartyInterfaceNamesWidget);
    }

    private void processRaidPartyInterface(Widget partyInterfaceNamesWidget) {
        //Process a party interface in the ToB or ToA lobby
        if (partyInterfaceNamesWidget == null || partyInterfaceNamesWidget.isHidden()) {
            //Widget is hidden among others inside ToB while the script will still proc inside ToB.
            return;
        }

        String raidPartyInterfaceText = partyInterfaceNamesWidget.getText();
        if (raidPartyInterfaceText.equals(previousRaidPartyInterfaceText)) {
            //Only process the widget if the text has changed compared to the previous processing
            return;
        }

        previousRaidPartyInterfaceText = raidPartyInterfaceText;
        raidPartyInterfaceText = raidPartyInterfaceText.concat("<br>"); //Append <br> so indexOf and substring works for every item
        for (int i = 0; i < 8; i++) {
            int idx = raidPartyInterfaceText.indexOf("<br>");
            if (idx != -1) {
                String standardizedUsername = Text.standardize(raidPartyInterfaceText.substring(0, idx));
                //Prevent empty strings or strings equalling "-" being added to the hashset.
                if (!Strings.isNullOrEmpty(standardizedUsername)
                        && !standardizedUsername.equals("-")
                        && raidPartyStandardizedUsernames.add(standardizedUsername)) {
                    //Since the user has to be in this party (can't view other parties like this), add to the real HashSet instead of a temp one
                    shouldRefreshChat = true;
                }
                raidPartyInterfaceText = raidPartyInterfaceText.substring(idx + 4); //get substring to remove first user and first <br> (idx+4 so resulting substring starts after the first <br>)
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
        if (!(varCStrIndex >= TOB_IN_RAID_VARCSTR_PLAYER1_INDEX && varCStrIndex <= TOB_IN_RAID_VARCSTR_PLAYER5_INDEX)
                && !(varCStrIndex >= TOA_IN_RAID_VARCSTR_PLAYER1_INDEX && varCStrIndex <= TOA_IN_RAID_VARCSTR_PLAYER8_INDEX)) {
            //If not tob or toa VarCStrIndex, return
            return;
        }

        String varCStrValueStandardized = Text.standardize(client.getVarcStrValue(varCStrIndex));
        //isNullOrEmpty check because they get refreshed in probably every room and can potentially add empty strings to the hashset.
        if (!Strings.isNullOrEmpty(varCStrValueStandardized) && raidPartyStandardizedUsernames.add(varCStrValueStandardized)) {
            shouldRefreshChat = true;
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
    }

    //private static final int TOA_PARTY_INTERFACE_NAMES_CHILDID = 5; //S773.5
    //No party text = -<br>-<br>-<br>-<br>-<br>-<br>-<br>-
    //Party text = Username<br>Username2<br>-<br>-<br>-<br>-<br>-<br>-
    private void processToAPartyInterface() {
        //Processed the party interface in the ToA lobby
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
        if (client.getVarpValue(VarPlayer.IN_RAID_PARTY) == -1) {
            return;
        }

        FriendsChatManager friendsChatManager = client.getFriendsChatManager();
        if (friendsChatManager == null) {
            return;
        }

        for (Player player : client.getPlayers()) {
            String standardizedUsername = Text.standardize(player.getName());
            if (friendsChatManager.findByName(standardizedUsername) != null
                    && !Strings.isNullOrEmpty(standardizedUsername)
                    && raidPartyStandardizedUsernames.add(standardizedUsername)) {
                shouldRefreshChat = true;
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
        if (!partyService.isInParty()) {
            //Return early if not in party
            return;
        }

        List<PartyMember> partyMembers = partyService.getMembers();
        if (partyMembers == null || partyMembers.isEmpty()) {
            return;
        }

        //The list is not empty anymore (can be empty immediately after joining a party) ->
        // set the flag to 0 and add the partymembers to the correct hashset
        getRLPartyMembersFlag = 0;
        for (PartyMember partyMember : partyMembers) {
            String standardizedUsername = Text.standardize(partyMember.getDisplayName());
            if (!Strings.isNullOrEmpty(standardizedUsername) && runelitePartyStandardizedUsernames.add(standardizedUsername)) {
                shouldRefreshChat = true;
            }
        }
        System.out.println(runelitePartyStandardizedUsernames); //todo: remove
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
                if (runelitePartyStandardizedUsernames.add(standardizedUsername)) {
                    shouldRefreshChat = true;
                }
            } else {
                //If a member is not processed correctly, set the boolean to false
                allMembersProcessed = false;
            }
        }
        //If no member has been processed too early (if the boolean has not been set to false), set flag to 0
        if (allMembersProcessed) {
            getRLPartyUserJoinedMembersFlag = 0;
        }
        System.out.println(runelitePartyStandardizedUsernames); //todo: remove
        if (getRLPartyUserJoinedMembersFlag == 0) { //Clear the hashset when flag = 0. setAddUserJoinedPartyStandardizedUsernamesFlag sets flag back to 5 in case a user joins while the flag is going down.
            partyMemberIds.clear();
        }
    }

    private boolean shouldShowShiftMenuSetting(ShiftMenuSetting shiftMenuSetting) {
        //Should the ClearRaidPartyMenu or ChangeChatSetsMenu be shown based on the advanced config setting?
        switch (shiftMenuSetting) {
            case ALWAYS:
                return true;
            case DISABLED:
                return false;
            case HOLD_SHIFT:
                return shiftModifier();
        }
        return false;
    }

    private boolean shiftModifier() {
        //Is shift pressed? Returns false if client is not focused
        return client.isKeyPressed(KeyCode.KC_SHIFT);
    }

    @Provides
    ChatFilterExtendedConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ChatFilterExtendedConfig.class);
    }
}