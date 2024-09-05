package com.ywcode.chatfilterextended;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
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
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.awt.Color;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@PluginDescriptor(
        name = "Chat Filter Extended",
        description = "Extends the functionality of the chat tabs/stones to filter chat messages not from friends/FC members/clan members/Guest CC members/raid members/RL party members/whitelisted people.",
        tags = {"chat,chat filter,CFE,public,public OH,friends,friends OH,fc,fc OH,cc,cc OH,guest,guest OH,raid,raid OH,party,party OH,whitelist,whitelist OH,custom,clanchat,clan,filter,friends chat,private,trade,raids,tob,toa,cox,spam,show,chat tab,chat stone"}
)
//Alternative (bad) names: Custom Chat Filter/Custom Chat Filters, Custom Chat View, Chat View Extended, Chat Show Custom, Chat tabs extended, Chatstones extended
//My goal was not to make one of these "abc improved" or "better abc" plugins, but the menuOptions like "Show friends" or "Show none" are just called chat filters, and I can't come up with a better name. At least Polar calls them that in e.g. script 152 (chat_set_filter)
//It's just unlucky that the chat filter plugin (which is a perfectly valid name for its function) is also called chat filter, I guess.

public class ChatFilterExtendedPlugin extends Plugin {

    // ------------- Wall of config vars -------------
    // Vars are quite heavily cached so could probably just config.configKey(). However, the best practice behavior in plugins is to have a bunch of variables to store the results of the config methods, and check it in startUp/onConfigChanged. It feels redundant, but it's better than hitting the reflective calls every frame. --LlemonDuck. Additionally, the sets are actually getting processed.
    private static boolean forcePrivateOn;
    private static boolean showGuestTrades;
    private static ShiftMenuSetting changeChatSetsShiftMenuSetting;
    private static ShiftMenuSettingOptional clearRaidPartyShiftMenuSetting;
    private static ShiftMenuSettingOptional autoEnableFilteredRegionShiftMenuSetting;
    private static final Set<String> publicWhitelist = new HashSet<>();
    private static final Set<String> privateWhitelist = new HashSet<>();
    private static final Set<String> channelWhitelist = new HashSet<>();
    private static final Set<String> clanWhitelist = new HashSet<>();
    private static final Set<String> tradeWhitelist = new HashSet<>();
    private static boolean clearChannelSetHop;
    private static boolean clearClanSetHop;
    private static boolean clearGuestClanSetHop;
    private static boolean clearRaidPartySetHop;
    private static boolean clearRLPartySetHop;
    private static boolean clearChannelSetLeave;
    private static boolean clearClanSetLeave;
    private static boolean clearGuestClanSetLeave;
    private static boolean clearRLPartySetLeave;
    private static boolean fixChatTabAlert; //todo: implement!
    private static boolean preventLocalPlayerChatTabAlert; //todo: implement!

    //The config values below are only part of the RSProfile
    //todo: add more RSProfile vars to this if you decide to add more configs to RSProfile
    private static boolean publicFilterEnabled; //i.e. if the user set the chat tab/stone to custom. So we can re-enable it on startup.
    private static boolean privateFilterEnabled; //i.e. if the user set the chat tab/stone to custom. So we can re-enable it on startup.
    private static boolean channelFilterEnabled; //i.e. if the user set the chat tab/stone to custom. So we can re-enable it on startup.
    private static boolean clanFilterEnabled; //i.e. if the user set the chat tab/stone to custom. So we can re-enable it on startup.
    private static boolean tradeFilterEnabled; //i.e. if the user set the chat tab/stone to custom. So we can re-enable it on startup.
    private static final Set<ChatTabFilter> publicOHChatFilters = EnumSet.noneOf(ChatTabFilter.class);
    private static final Set<ChatTabFilter> publicChatFilters = EnumSet.noneOf(ChatTabFilter.class);
    private static final Set<ChatTabFilter> privateChatFilters = EnumSet.noneOf(ChatTabFilter.class);
    private static final Set<ChatTabFilter> channelChatFilters = EnumSet.noneOf(ChatTabFilter.class);
    private static final Set<ChatTabFilter> clanChatFilters = EnumSet.noneOf(ChatTabFilter.class);
    private static final Set<ChatTabFilter> tradeChatFilters = EnumSet.noneOf(ChatTabFilter.class);
    // ------------- End of wall of config vars -------------

    //Variables
    //todo: probably sort these
    private static boolean areRSProfileDefaultsSet; //This is required because setting the defaults using setRuneScapeProfileConfiguration procs onConfigChanged -> NPE. Thus, we need to know when the defaults have been set.
    private static boolean setChatsToPublicFlag; //Default value is false
    private static GameState previousPreviousGameState;
    private static GameState previousGameState;
    private static final Set<String> channelStandardizedUsernames = new HashSet<>();
    private static final Set<String> clanMembersStandardizedUsernames = new HashSet<>();
    private static final Set<String> clanTotalStandardizedUsernames = new HashSet<>();
    private static final Set<String> guestClanMembersStandardizedUsernames = new HashSet<>();
    private static final Set<String> guestClanTotalStandardizedUsernames = new HashSet<>();
    private static final Set<String> raidPartyStandardizedUsernames = new HashSet<>();
    private static final Set<String> runelitePartyStandardizedUsernames = new HashSet<>();
    private static boolean inCoXRaidOrLobby; //Default value is false
    private static int getRLPartyMembersFlag; //Default is 0
    private static boolean shouldRefreshChat; //Default is false
    private static String previousRaidPartyInterfaceText; //null by default
    private static final Set<Long> partyMemberIds = new HashSet<>();
    private static int getRLPartyUserJoinedMembersFlag; //Default is 0
    @Getter(AccessLevel.PACKAGE)
    @Setter(AccessLevel.PACKAGE)
    private static String filteredRegionsMalformedString; //Arguable this should just be declared in FilteredRegionAdapter with a getter
    private static final Map<Integer, FilteredRegion> filteredRegions = new HashMap<>(); //Using map so I don't have to loop through a Set to get a FilteredRegion based on its regionID
    private static int previousRegionID; //todo: potentially convert to local variable
    private static int currentRegionID; //todo: potentially convert to local variable
    private static boolean isInFilteredRegion; //Class-wide static variable so I don't have to recheck if filteredRegions contains the regionID in case currentRegionID = previousRegionID //todo: potentially convert to local variable
    private static boolean wasInFilteredRegion; //todo: potentially convert to local variable
    //Collection cheat sheet: https://i.stack.imgur.com/POTek.gif (that I probably did not fully adhere to lol)

    //Constants
    //todo: probably sort these
    private static final String CONFIG_GROUP = "ChatFilterExtended";
    private static final String CHAT_COLOR_CONFIG_GROUP = "textrecolor";
    private static final List<Integer> CHATBOX_COMPONENT_IDS = ImmutableList.of(ComponentID.CHATBOX_TAB_PUBLIC, ComponentID.CHATBOX_TAB_PRIVATE, ComponentID.CHATBOX_TAB_CHANNEL, ComponentID.CHATBOX_TAB_CLAN, ComponentID.CHATBOX_TAB_TRADE);
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

    private Gson gson;

    @Override
    public void startUp() {
        gson = injector.getInstance(Gson.class).newBuilder().setLenient().create();
        //Set to lenient so it accepts e.g. Names and Strings that are unquoted or 'single quoted'.
        // See https://www.javadoc.io/doc/com.google.code.gson/gson/2.8.5/com/google/gson/stream/JsonReader.html#setLenient-boolean- for more info
        // Maybe this is not needed in the currently bundled Gson 2.8.5, but it might be required for 2.9.0 because of https://github.com/google/gson/pull/1989 / https://github.com/google/gson/blob/main/CHANGELOG.md#version-290
        // So just do it to be sure in case the ancient core Gson version is ever updated and it does actually matter in >= 2.9.0
        setRSProfileConfigFirstStart();
        updateConfig();
        updateFilteredRegions();
        //PM Config keys that are not part of ChatFilterExtendedConfig are still empty on first startup ->
        // in case you readd those types of keys, prevent them being null by setting them before other code checks the
        // config keys. Do this both on startUp AND ProfileChanged!
        //Idem for all RSProfile keys, but this needs to be done on startUp and RuneScapeProfileChanged

        clientThread.invokeLater(() -> {
            setChatsToPublic(); //Chats are only being set to public if the filter for that chatstone is active!
            addAllInRaidUsernamesVarClientStr(); //Will also add a raid group to the HashSet if you are not inside ToB/ToA anymore. This is fine and can be useful in certain situations, e.g. getting a scythe, teleporting to the GE to get the split and then turning on the plugin at the GE. You can still see your raid buddies' messages then. If this is undesired, replace with addToBPlayers() and addToAPlayers()
            setAddPartyMembersFlag(); //In case the plugin is started while already in a party.
            getCoXVarbit(); //Get varbit in case the plugin is started while logged in.
            addCoXPlayers(); //Get CoX players because it does not trigger onPlayerSpawned while inside a raid if the players have already spawned before the plugin is turned on.
            processToBBoard(); //User might technically enable plugin and exit the ToB board before the refresh scriptid procs.
            processToABoard(); //User might technically enable plugin and exit the ToA board before the refresh scriptid procs.
            addFCMembers(); //In case the plugin is started while already in an FC.
            addCCMembers(); //In case the plugin is started while already in a CC.
            addGuestCCMembers(); //In case the plugin is started while already in a guest CC.
        });

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
        //todo: ctrl+shift+f return null en check of je het met @Nullable annotated hebt
        //todo: maak mss nog wat variables final?
        //todo: kijk for loops nog na of je niet meer breaks, continues, of returns toe kan voegen

        //todo: probs clear some more sets and maps here
        partyMemberIds.clear();
        filteredRegions.clear();
        channelStandardizedUsernames.clear();
        clanMembersStandardizedUsernames.clear();
        clanTotalStandardizedUsernames.clear();
        guestClanMembersStandardizedUsernames.clear();
        guestClanTotalStandardizedUsernames.clear();
        runelitePartyStandardizedUsernames.clear();
        clearRaidPartySet(); //Also clear the string so the plugin will process the party interface if needed
        if (client.getGameState() == GameState.LOGGED_IN || client.getGameState() == GameState.LOADING) {
            clientThread.invoke(() -> {
                //This rebuilds both the chatbox and the pmbox
                client.runScript(ScriptID.SPLITPM_CHANGED);
                client.runScript(REDRAW_CHAT_BUTTONS_SCRIPTID); //[proc,redraw_chat_buttons]
            });
        }
        //Clearing these probably doesn't matter that much; at least the EnumSets are limited based on the relatively small total number of enum elements, but clearing can't hurt.
        publicWhitelist.clear();
        privateWhitelist.clear();
        channelWhitelist.clear();
        clanWhitelist.clear();
        tradeWhitelist.clear();
        publicOHChatFilters.clear();
        publicChatFilters.clear();
        privateChatFilters.clear();
        channelChatFilters.clear();
        clanChatFilters.clear();
        tradeChatFilters.clear();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged configChanged) {
        if (configChanged.getGroup().equals(CONFIG_GROUP)) {
            //No early return rn bec I might also want to check events for another group in the future
            //PM Also procs for configManager.setRSProfileConfiguration(CONFIG_GROUP, keyName, value)
            final String changedKey = configChanged.getKey();
            updateConfig();
            disableFilterWhenSetEmptied();
            if (changedKey.equals("forcePrivateOn")) {
                if (forcePrivateOn) {
                    //Set friends to on when forePrivateOn is enabled and chat is custom filtered
                    setChatsToPublic();
                } else { //if (!forcePrivateOn)
                    //Set friends status to non filtered and redraw chat buttons to show the current state of friends -> also sets the other chats to custom again if needed because redrawChatButtons procs a script that triggers the code setting the text of the chat buttons.
                    setChatFilterConfig(ComponentID.CHATBOX_TAB_PRIVATE, false);
                    redrawChatButtons();
                }
            }
            if (changedKey.equals("filteredRegionsData")) {
                //Only update it when this value changes because maybe some people have an insane amount of regions?
                updateFilteredRegions();
            }
            client.refreshChat(); //Refresh chat when the config changes (enabling/disabling filter, changing filter settings).
        }
        //todo: probably add boolean button like client resizer (LinkBrowser thing) to reset trade chat related settings for configprofile and rsprofile? -> could be useful in case you fucked with it and want to reset it to semi-safe defaults? -> alternatively use a chat command, but this is less visible to the user
    }

    private void updateConfig() {
        forcePrivateOn = config.forcePrivateOn();
        showGuestTrades = config.showGuestTrades();
        changeChatSetsShiftMenuSetting = config.changeChatSetsShiftMenuSetting();
        clearRaidPartyShiftMenuSetting = config.clearRaidPartyShiftMenuSetting();
        autoEnableFilteredRegionShiftMenuSetting = config.autoEnableFilteredRegionShiftMenuSetting();
        convertCommaSeparatedStringToSet(config.publicWhitelist(), publicWhitelist);
        convertCommaSeparatedStringToSet(config.privateWhitelist(), privateWhitelist);
        convertCommaSeparatedStringToSet(config.channelWhitelist(), channelWhitelist);
        convertCommaSeparatedStringToSet(config.clanWhitelist(), clanWhitelist);
        convertCommaSeparatedStringToSet(config.tradeWhitelist(), tradeWhitelist);
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

        //The config values below are part of the RSProfile
        if (areRSProfileDefaultsSet) {
            //The RSProfile has been loaded, and defaults have been set
            //PS Probs don't try to refactor this; did not go well (on plugin start) the last time I tried that...
            publicFilterEnabled = configManager.getRSProfileConfiguration(CONFIG_GROUP, ChatTab.PUBLIC.getFilterEnabledKeyName(), boolean.class);
            privateFilterEnabled = configManager.getRSProfileConfiguration(CONFIG_GROUP, ChatTab.PRIVATE.getFilterEnabledKeyName(), boolean.class);
            channelFilterEnabled = configManager.getRSProfileConfiguration(CONFIG_GROUP, ChatTab.CHANNEL.getFilterEnabledKeyName(), boolean.class);
            clanFilterEnabled = configManager.getRSProfileConfiguration(CONFIG_GROUP, ChatTab.CLAN.getFilterEnabledKeyName(), boolean.class);
            tradeFilterEnabled = configManager.getRSProfileConfiguration(CONFIG_GROUP, ChatTab.TRADE.getFilterEnabledKeyName(), boolean.class);
            convertSetToEnumSet(getChatTabFiltersRSProfile(ChatTab.getPublicOHChatFiltersKeyName()), publicOHChatFilters);
            convertSetToEnumSet(getChatTabFiltersRSProfile(ChatTab.PUBLIC.getChatTabFiltersKeyName()), publicChatFilters);
            convertSetToEnumSet(getChatTabFiltersRSProfile(ChatTab.PRIVATE.getChatTabFiltersKeyName()), privateChatFilters);
            convertSetToEnumSet(getChatTabFiltersRSProfile(ChatTab.CHANNEL.getChatTabFiltersKeyName()), channelChatFilters);
            convertSetToEnumSet(getChatTabFiltersRSProfile(ChatTab.CLAN.getChatTabFiltersKeyName()), clanChatFilters);
            convertSetToEnumSet(getChatTabFiltersRSProfile(ChatTab.TRADE.getChatTabFiltersKeyName()), tradeChatFilters);
            //todo: add other rsprofile configs to this if you decide to add more rsprofile values
        }
    }

    @Subscribe
    public void onProfileChanged(ProfileChanged profileChanged) {
        //todo: in case you add any config keys that are not in CFEConfig, do a setConfigFirstStart
        previousRegionID = 0; //ProfileChanged fires after ConfigChanged. Between config profiles the filtered regions may differ, so force a recheck by doing this
    }

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged runeScapeProfileChanged) {
        areRSProfileDefaultsSet = false; //Defaults might have not been set yet, so reset to false
        setRSProfileConfigFirstStart(); //Set defaults in case they don't exist yet. Also set areRSProfileDefaultsSet to true, which will be used in updateConfig()
        updateConfig(); //Load updated RSProfile settings
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        switch (gameStateChanged.getGameState()) {
            case LOGGED_IN:
                if (previousGameState == GameState.LOADING &&
                        (previousPreviousGameState == GameState.LOGGING_IN || previousPreviousGameState == GameState.HOPPING)) {
                    //Alternatively just set a flag on LOGGING_IN and HOPPING lol
                    setChatsToPublic();
                    setAddPartyMembersFlag(); //This will ensure that the party HashSet is also correctly populated after fully logging out but remaining in the party.
                }
                break;
            case LOGIN_SCREEN:
                previousRegionID = 0; //Reset cause people can log out, move on a different client and log back in
                //Resetting wasInFilteredRegion is not necessary and should probs not be done here. It's used as a way to determine if the current custom filter state needs to be changed, not specifically if the region the user is in is filtered or not
                //todo: wasInFilteredRegion and previousRegionID etc. might have to be reset when changing RSProfile though... Think about this.
                channelStandardizedUsernames.clear();
                clanMembersStandardizedUsernames.clear();
                clanTotalStandardizedUsernames.clear();
                guestClanMembersStandardizedUsernames.clear();
                guestClanTotalStandardizedUsernames.clear();
                runelitePartyStandardizedUsernames.clear();
                clearRaidPartySet(); //Also clear the string so the plugin will process the party interface if needed + shouldRefreshChat = true
                break;
            case HOPPING:
                if (clearRaidPartySetHop) {
                    clearRaidPartySet(); //Also clear the string so the plugin will process the party interface if needed
                }
                //Clear RL party members by default while hopping because you generally don't care about them anymore after hopping to another world (or they will be re-added then)
                if (clearRLPartySetHop) {
                    runelitePartyStandardizedUsernames.clear();
                }
                if (clearChannelSetHop) {
                    channelStandardizedUsernames.clear();
                }
                if (clearClanSetHop) {
                    clanMembersStandardizedUsernames.clear();
                    clanTotalStandardizedUsernames.clear();
                }
                if (clearGuestClanSetHop) {
                    guestClanMembersStandardizedUsernames.clear();
                    guestClanTotalStandardizedUsernames.clear();
                }
                shouldRefreshChat = true;
                break;
                //todo: potentially add CONNECTION_LOST; check core chat filter plugin
        }
        previousPreviousGameState = previousGameState; //Yes, I'm a magician with words.
        previousGameState = gameStateChanged.getGameState();
    }

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
        //Since it's a HashSet, the item isn't inserted if it's a duplicate => so no .contains check beforehand needed.
        //In case the HashSet already contains the value, it'll return false, so the boolean will not be set to true
        if (channelStandardizedUsernames.add(Text.standardize(friendsChatMemberJoined.getMember().getName()))) {
            shouldRefreshChat = true;
        }
    }

    @Subscribe
    public void onClanChannelChanged(ClanChannelChanged clanChannelChanged) {
        //If left CC => clear own cc usernames HashSet
        final ClanChannel clanChannel = clanChannelChanged.getClanChannel();
        if (!clanChannelChanged.isGuest()) { //If left or joined own CC or GIM chat
            final int clanId = clanChannelChanged.getClanId();
            if (clanId == ClanID.CLAN) { //If left/joined own CC, separate line because of all the if-then-else statements used here
                if (client.getClanChannel(ClanID.CLAN) == null) { //If not in own CC -> left CC
                    if (clearClanSetLeave) {
                        clanMembersStandardizedUsernames.clear();
                        clanTotalStandardizedUsernames.clear();
                        shouldRefreshChat = true;
                    }
                } else { //If in own CC (i.e. your clan, not a guest clan)
                    //If joined own CC, get members and add the usernames to HashSet
                    addClanMembers(clanChannel, client.getClanSettings(ClanID.CLAN));
                }
            }

            //Also include GIM members in Clan Hashset, untested because no access to a GIM account
            if (clanId == ClanID.GROUP_IRONMAN //If joined/left GIM chat
                    && client.getClanChannel(ClanID.GROUP_IRONMAN) != null) { //If in GIM chat
                addClanMembers(clanChannel, client.getClanSettings(ClanID.GROUP_IRONMAN));
            }

        } else { //If left/joined guest CC
            if (client.getGuestClanChannel() == null) { //If not in guest CC
                if (clearGuestClanSetLeave) { //If left guest CC => clear guest cc usernames HashSet if the advanced config option is enabled
                    guestClanMembersStandardizedUsernames.clear();
                    guestClanTotalStandardizedUsernames.clear();
                    shouldRefreshChat = true;
                }
            } else { //If joined guest clan
                //If joined guest clan, get members and add the usernames to HashSet
                addGuestClanMembers(clanChannel, client.getGuestClanSettings());
            }
        }
    }

    @Subscribe
    public void onClanMemberJoined(ClanMemberJoined clanMemberJoined) {
        //Add newly joined guests this way since the HashSet does not contain clan/guest clan guests yet
        //The normal members should already be added as part of the clansettings check earlier
        final String standardizedJoinedName = Text.standardize(clanMemberJoined.getClanMember().getName());
        final ClanChannel clanChannelJoined = clanMemberJoined.getClanChannel();

        //If person joins clan/GIM chat, add to clan HashSet. PS switch does not like clanMemberJoined.getClanChannel()
        if ((clanChannelJoined.equals(client.getClanChannel(ClanID.CLAN))
                || clanChannelJoined.equals(client.getClanChannel(ClanID.GROUP_IRONMAN))) //Alternatively use clanChannel != null && clanChannel.findMember(standardizedJoinedName) != null -> //findMember works both with .removeTags and with .standardize
                && clanTotalStandardizedUsernames.add(standardizedJoinedName)) {
            shouldRefreshChat = true;
        }

        //If person joins guest CC chat, add to guest clan HashSet.
        if (clanChannelJoined.equals(client.getGuestClanChannel()) //Alternatively use guestClanChannel != null && guestClanChannel.findMember(standardizedJoinedName) != null
                && guestClanTotalStandardizedUsernames.add(standardizedJoinedName)) {
            shouldRefreshChat = true;
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
            //System.out.println(this.chatTabAlerts.get(1).isFiltered());
            //System.out.println(this.chatTabAlerts.get(1).isOwnMessage());
            //System.out.println(this.chatTabAlerts.get(1).getChatTabNumber());
            //System.out.println(this.chatTabAlerts.get(1).getVarcIntCountdownValue());
            //System.out.println("total entries: "+this.chatTabAlerts.size());
        }
        if (commandExecuted.getCommand().equals("test3")) {
            for (Map.Entry<Integer, FilteredRegion> entry :  filteredRegions.entrySet()) {
                System.out.println("Key = " + entry.getKey());
                FilteredRegion region = entry.getValue();
                //System.out.println("getRegionID = " + region.getRegionID());
                System.out.println("isPublicChatCustomOnly = " + region.isPublicChatCustomOnly());
                System.out.println("getPublicOHChatSet = " + region.getPublicOHChatSet());
                System.out.println("getPublicChatSet = " + region.getPublicChatSet());
                System.out.println("isPrivateChatCustomOnly = " + region.isPrivateChatCustomOnly());
                System.out.println("getPrivateChatSet = " + region.getPrivateChatSet());
                System.out.println("isChannelChatCustomOnly = " + region.isChannelChatCustomOnly());
                System.out.println("getChannelChatSet = " + region.getChannelChatSet());
                System.out.println("isClanChatCustomOnly = " + region.isClanChatCustomOnly());
                System.out.println("getClanChatSet = " + region.getClanChatSet());
                System.out.println("isTradeChatCustomOnly = " + region.isTradeChatCustomOnly());
                System.out.println("getTradeChatSet = " + region.getTradeChatSet());
                System.out.println("======================================");
            }
        }
        if (commandExecuted.getCommand().equals("test4")) {
            areRSProfileDefaultsSet = false;
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, ChatTab.PUBLIC.getFilterEnabledKeyName());
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, ChatTab.PRIVATE.getFilterEnabledKeyName());
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, ChatTab.CHANNEL.getFilterEnabledKeyName());
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, ChatTab.CLAN.getFilterEnabledKeyName());
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, ChatTab.TRADE.getFilterEnabledKeyName());
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, ChatTab.getPublicOHChatFiltersKeyName());
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, ChatTab.PUBLIC.getChatTabFiltersKeyName());
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, ChatTab.PRIVATE.getChatTabFiltersKeyName());
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, ChatTab.CLAN.getChatTabFiltersKeyName());
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, ChatTab.CHANNEL.getChatTabFiltersKeyName());
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, ChatTab.TRADE.getChatTabFiltersKeyName());
            System.out.println("rsprofile settings unset");
        }
        if (commandExecuted.getCommand().equalsIgnoreCase("test5")) {
            String testString1 = gson.toJson(filteredRegions);
            System.out.println(testString1);
            for (Map.Entry<Integer, FilteredRegion> entry :  filteredRegions.entrySet()) {
                System.out.println("Key = " + entry.getKey());
                FilteredRegion region = entry.getValue();
                System.out.println("isPublicChatCustomOnly = " + region.isPublicChatCustomOnly());
                System.out.println("getPublicOHChatSet = " + region.getPublicOHChatSet());
                System.out.println("getPublicChatSet = " + region.getPublicChatSet());
                System.out.println("isPrivateChatCustomOnly = " + region.isPrivateChatCustomOnly());
                System.out.println("getPrivateChatSet = " + region.getPrivateChatSet());
                System.out.println("isChannelChatCustomOnly = " + region.isChannelChatCustomOnly());
                System.out.println("getChannelChatSet = " + region.getChannelChatSet());
                System.out.println("isClanChatCustomOnly = " + region.isClanChatCustomOnly());
                System.out.println("getClanChatSet = " + region.getClanChatSet());
                System.out.println("isTradeChatCustomOnly = " + region.isTradeChatCustomOnly());
                System.out.println("getTradeChatSet = " + region.getTradeChatSet());
                System.out.println("======================================");
            }
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
        final int[] intStack = client.getIntStack();
        final int intStackSize = client.getIntStackSize();
        final int messageType = intStack[intStackSize - 2];
        final ChatMessageType chatMessageType = ChatMessageType.of(messageType);

        if (!isChatTabFilterEnabled(chatMessageType)) {
            //if chat is not filtered, return
            //shouldFilter already has a Strings.isNullOrEmpty(playerName) check
            return;
        }

        //Get the playerName
        final int messageId = intStack[intStackSize - 1];
        final MessageNode messageNode = client.getMessages().get(messageId);
        final String playerName = messageNode.getName();

        final Set<ChatTabFilter> chatTabFilters = getChatTabFilters(chatMessageType);
        //ChatMessage that IS part of the publicChatFilters set => needs to incorporate !publicOHChatFilters.contains in all of it (if in non-OH set => if not in overhead set => return false)
        //ChatMessage that is NOT part of the publicChatFilters set => works perfectly with shouldFilterMessage
        final boolean shouldFilter = chatTabFilters == publicChatFilters ? shouldFilterMessagePublicChatMessage(playerName) : shouldFilterMessage(chatTabFilters, playerName);
        if (shouldFilter) {
            // Block the message
            System.out.println("Blocked message by " + Text.standardize(playerName)); //todo: remove
            intStack[intStackSize - 3] = 0;
        }
    }

    // Setting the priority is very important; otherwise it will race with other plugins such as probably core's ChatFilter and not hide the text!
    @Subscribe (priority = -10)
    public void onOverheadTextChanged(OverheadTextChanged overheadTextChanged) {
        //Overheads => the appropriate set is always publicChatFilters set => works perfectly with shouldFilterMessage
        final Actor actor = overheadTextChanged.getActor();
        if (!(actor instanceof Player) || !isChatTabFilterEnabled(ChatMessageType.PUBLICCHAT)) {
            //So e.g. Bob Barter (Herbs) at the GE doesn't get filtered
            //if chat is not filtered, return
            //shouldFilter already has a Strings.isNullOrEmpty(playerName) check
            return;
        }

        final boolean shouldFilter = shouldFilterMessage(publicChatFilters, actor.getName()); //Yes, using this method and not the OH (public chat message) one is correct here. When the OH option is active, it shouldFilter the chatbox chat, but not the OH chat! Activating/deactivating the OH option does not remove/filter the OH message!
        if (shouldFilter) {
            actor.setOverheadText(" ");
            System.out.println(Text.standardize(actor.getName()) + " OH text filtered"); //todo: remove
        }
    }

    @Subscribe(priority = -2) //Run after ChatHistory plugin etc. Probably not necessary but can't hurt
    public void onMenuEntryAdded(MenuEntryAdded menuEntryAdded) {
        //todo: fix the too long menu entry bug is still a thing with all of public + public oh enable
        //Add right-click option(s) and potentially submenus to the chatstones
        //PM you can't store menu entries since they will change next frame (source: Abex)
        if (menuEntryAdded.getType() != MenuAction.CC_OP.getId()) {
            return;
        }

        String menuEntryOption = menuEntryAdded.getOption();
        if (!menuEntryOption.contains("Show none")) {
            //return early if the menu entry option does not contain show none. ComponentID is checked a bit below via if (chatTabFilters == null)
            return;
        }

        //getActionParam1() seems to be getMenuEntry().getParam1() which seems to be getMenuEntry().getWidget().getId() = 10616843 = ComponentID (for public chat).
        final int menuEntryAddedParam1 = menuEntryAdded.getActionParam1();
        final Set<ChatTabFilter> chatTabFilters = getChatTabFilters(menuEntryAddedParam1);
        final Set<ChatTabFilter> OHChatTabFilters = getOHChatTabFilters(menuEntryAddedParam1);
        //Sets have been retrieved from the componentID. Returns null when componentID != chatstone componentID

        //Don't need to check OHChatTabFilters, since it uses the same componentID as the chatTabFilters (both that of the public chat keystone)
        if (chatTabFilters == null) {
            //Not a chatstone componentID -> return
            return;
        }

        MenuEntry chatFilterEntry = null; //Used below to show a Change Sets Menu when the chatTabFilters set is completely empty, if it should show something (based on shift-click settings etc.).
        int mainMenuIdx = -1; //Index for main menu to be used below with client.getMenu().createMenuEntry(mainMenuIdx--)

        //Determine if the chatstone (e.g. private) should be filtered based on the componentID and the config set
        //shouldFilterChatType already has a ComponentID check build in that checks if it's a chatstone or not + checks if the filter option is enabled or not (if chatTabFilters is empty or not)
        if (shouldFilterChatType(menuEntryAddedParam1)) {
            //Prepare the name (option) of the main/parent MenuEntry
            final StringBuilder optionBuilder = new StringBuilder();
            //Pull tab name from menu since Trade/Group is variable + chatTabFilters MenuName based on config settings
            final int colonIdx = menuEntryOption.indexOf(':');
            if (colonIdx != -1) {
                optionBuilder.append(menuEntryOption, 0, colonIdx).append(":</col> ");
            }
            optionBuilder.append("Show ");
            //At this point the </col> tag has been closed and "Show " has been added, e.g. "<col>Public:</col> Show "

            //Grab the abbreviations from the enum based on the selected config
            //Elements in an EnumSet are stored following the order in which they are declared in the enum, so we don't have to loop the enum and check if chatTabFilters contains the value
            for (ChatTabFilter chatTabFilter : chatTabFilters) {
                optionBuilder.append(chatTabFilter.getAbbreviation()).append("/");
            }
            //At this point the name/option is e.g. "<col>Public:</col> Show Friends/FC/CC/"
            menuEntryOption = optionBuilder.toString();

            //Replace entries with their OH equivalent if OH is added to the OHChatTabFilters set
            //This is because I made the design decision that the chat filter (e.g. CC) needs to be enabled (visible) AND the OH chat filter (e.g. CC OH) needs to be active to only show CC OH
            //Order does not matter since I'm just replacing, so just iterate over OHChatTabFilters -> EnumSet is in order of declaration of the enum anyway
            if (OHChatTabFilters != null) { //Already checks if componentID = public chat by returning null if it's not public.
                for (ChatTabFilter chatTabFilter : OHChatTabFilters) {
                    menuEntryOption = menuEntryOption.replace(chatTabFilter.getAbbreviation() + "/", chatTabFilter.getOHAbbreviation() + "/"); //A slash is added, so it does not result in: "Public OH: Show Public OH/Friends/CC OH
                }
            }
            //At this point the name/option is e.g. "<col>Public:</col> Show Friends/FC/CC OH/"
            menuEntryOption = menuEntryOption.substring(0, menuEntryOption.length() - 1); //Remove the trailing "/". If deleted earlier, the final option is not properly replaced by its OH variant.
            //At this point the name/option is e.g. "<col>Public:</col> Show Friends/FC/CC OH"

            //Option is completed at this point. Create the main MenuEntry and set its params
            chatFilterEntry = client.getMenu().createMenuEntry(mainMenuIdx--)
                    .setType(MenuAction.RUNELITE)
                    .setParam1(menuEntryAddedParam1)
                    .onClick(e -> enableChatFilter(menuEntryAddedParam1))
                    .setOption(menuEntryOption);

            //If the ClearRaidPartyMenu should be shown, based on the advanced setting and shift state & only add if that chat is currently filtered
            if (shouldShowShiftMenuSetting(clearRaidPartyShiftMenuSetting) && isChatFiltered(menuEntryAddedParam1)) { //If you also want to display this when the custom filter is not enabled but the show custom option is shown for the tab, remove " && isChatFiltered(menuEntryAddedParam1)"
                client.getMenu().createMenuEntry(mainMenuIdx--)
                        .setType(MenuAction.RUNELITE)
                        .setParam1(menuEntryAddedParam1)
                        .onClick(this::clearRaidPartySetManually)
                        .setOption("Clear Raid Party members");
            }

            //If the autoEnableFilteredRegion should be shown, based on the advanced setting and shift state & only add if that chat is currently filtered
            if (shouldShowShiftMenuSetting(autoEnableFilteredRegionShiftMenuSetting) && isChatFiltered(menuEntryAddedParam1)) { //If you also want to display this when the custom filter is not enabled but the show custom option is shown for the tab, remove " && isChatFiltered(menuEntryAddedParam1)"
                final int regionID = currentRegionID; //Maybe not necessary but currentRegionID can change while moving, while regionID will not
                final MenuEntry autoEnableFilteredRegionEntry = client.getMenu().createMenuEntry(mainMenuIdx--)
                        .setType(MenuAction.RUNELITE)
                        .setParam1(menuEntryAddedParam1)
                        .setOption("Auto-enable custom (region " + regionID + ")");

                //Create the submenu, add the submenu entries, first justCustom, then the specific set, then remove
                final Menu autoEnableFilteredRegionEntrySubMenu = autoEnableFilteredRegionEntry.createSubMenu();
                //Add the submenu entries
                int submenuIdx = -1;
                autoEnableFilteredRegionEntrySubMenu.createMenuEntry(submenuIdx--)
                        .setType(MenuAction.RUNELITE)
                        .setOption("Set to Custom")
                        .onClick(e -> addFilteredRegionValue(regionID, menuEntryAddedParam1)); //Adds/updates the value for the FileredRegion field for this specific ChatTab (justCustom)

                autoEnableFilteredRegionEntrySubMenu.createMenuEntry(submenuIdx--)
                        .setType(MenuAction.RUNELITE)
                        .setOption("Set to current Custom set")
                        .onClick(e -> addFilteredRegionValue(regionID, menuEntryAddedParam1, chatTabFilters, OHChatTabFilters)); //Adds/updates the value for the FileredRegion field for this specific ChatTab (!justCustom, so the actual sets)

                if (isChatTabFilteredRegion(regionID, menuEntryAddedParam1)) {
                    autoEnableFilteredRegionEntrySubMenu.createMenuEntry(submenuIdx--)
                            .setType(MenuAction.RUNELITE)
                            .setOption("Remove auto-enable")
                            .onClick(e -> removeFilteredRegionValue(regionID, menuEntryAddedParam1)); //Removes the value for the FileredRegion field for this specific ChatTab
                }
            }
        }

        //Try to show a Change Sets Menu when the set is completely empty. Otherwise, add the submenu to chatFilterEntry! chatFilterEntry can get pretty long and it's difficult selecting the submenu otherwise due to the wide right click menu and the submenu usually showing up to the right.
        if (shouldShowShiftMenuSetting(changeChatSetsShiftMenuSetting)) { //If the ChangeSetsMenu should be shown, based on the advanced setting and shift state & only add if that chat is currently filtered
            //If the chat set is fully empty, add a parent menuentry!
            if (chatFilterEntry == null) { //aka if (!shouldFilterChatType(menuEntryAddedParam1))
                //Create parent menu. Could probably use MoreObjects.firstNonNull instead (assuming it doesn't also execute the code for the second if the first is not null), but meh.
                chatFilterEntry = client.getMenu().createMenuEntry(mainMenuIdx--)
                        .setType(MenuAction.RUNELITE)
                        .setParam1(menuEntryAddedParam1)
                        .setOption("Change Chat Sets");
            }
            //If the set with chats is not empty, we can create a submenu and add it to the MenuEntry we added in the beginning (chatFilterEntry)
            //If the set is empty, we just created it and also haven't created a submenu yet.
            final Menu chatFilterEntrySubMenu = chatFilterEntry.createSubMenu();

            //Add the submenu entries, use enum.values() to loop over the values.
            int submenuIdx = -1;
            for (ChatTabFilter chatTabFilter : ChatTabFilter.values()) {
                final StringBuilder optionBuilder = getSubMenuAddRemoveStringBuilder(chatTabFilters.contains(chatTabFilter), chatTabFilter.getMenuName());
                chatFilterEntrySubMenu.createMenuEntry(submenuIdx--)
                        .setType(MenuAction.RUNELITE)
                        .setOption(optionBuilder.toString())
                        .onClick(e -> addRemoveValueFromChatSet(chatTabFilters, chatTabFilter, menuEntryAddedParam1)); //Adds or removes to/from the chatTabFilters set, based on if the value is already in the set or not.
            }

            //Add the OH set to the public submenu if it's the public ComponentID
            if (OHChatTabFilters != null) { //Already checks if componentID = public chat (it's null if it's not public)
                submenuIdx = -1;
                for (ChatTabFilter chatTabFilter : ChatTabFilter.values()) {
                    final StringBuilder optionBuilder = getSubMenuAddRemoveStringBuilder(OHChatTabFilters.contains(chatTabFilter), chatTabFilter.getOHMenuName());
                    chatFilterEntrySubMenu.createMenuEntry(submenuIdx--)
                            .setType(MenuAction.RUNELITE)
                            .setOption(optionBuilder.toString())
                            .onClick(e -> addRemoveValueFromOHChatSet(OHChatTabFilters, chatTabFilter)); //Adds or removes to/from the OHChatTabFilters set, based on if the value is already in the set or not.
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
        if (isChatStone(menuOptionClickedParam1) && menuOption.contains("Show")) { //alternatively you could use menuOption.contains("<col=ffff00>") && menuOption.contains("Show") ?
            //Remove everything before the : so it doesn't match <col=ffff00>Public:</col>
            final int idx = menuOption.indexOf(':');
            if (idx != -1) {
                menuOption = menuOption.substring(idx);
            }
            for (ChatTabFilter enumValue : ChatTabFilter.values()) { //All the OHAbbreviations contain the non-OH abbreviation so contains still matches
                //Alternatively get the set and OHSet, then loop through the abbreviation but meh
                if (menuOption.contains(enumValue.getAbbreviation())) { //Plugin uses Friends instead of friends (osrs game)
                    //return in case it is the menu entry/option we added ourselves! We do not want to turn off the filter when clicking on our menu entry/option.
                    return;
                }
            }
            //If the specific chat is filtered, disable the filter. Technically the if statement could potentially be skipped.
            if (isChatFiltered(menuOptionClickedParam1)) {
                setChatFilterConfig(menuOptionClickedParam1, false);
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        currentRegionID = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID();

        //If filteredRegionsMalformedString != null -> send message and null it
        if (filteredRegionsMalformedString != null) {
            actuallySendMessage(getColoredPluginName() + "FilteredRegions error. Likely the JSON String you inputted is malformed: " + filteredRegionsMalformedString);
            filteredRegionsMalformedString = null; //Reset filteredRegionsMalformedString
            //Could technically add a cooldown, but this only procs once per GameTick and FilteredRegionAdapter's
            // JsonReader should not take long enough for it to be more than 2 messages at worst.
            // (if there's a message right before the GameTick event fires and right after)
        }

        //If regionid changed, or when previousRegionID is default (0)
        if (currentRegionID != previousRegionID) {
            isInFilteredRegion = filteredRegions.containsKey(currentRegionID);
            if (isInFilteredRegion) { //todo: potentially clean this up if variable just becomes a local variable, maybe add some comments
                //todo: setting chat back to e.g. public, private etc. For fc and cc maybe just rebuilding after turning off is enough? (see next line)
                // => Put the scriptargs in an enum


                //todo: add advanced config option, enabled by default, regarding a chat message for enabling/disabling for automatic stuff in filteredregion
            }
        }

        if (setChatsToPublicFlag) {
            executeSetChatsToPublic();
            setChatStoneWidgetTextAll(); //Also executed in setChatsToPublic() (not to be confused with executeSetChatsToPublic) to improve the feeling (makes it feel snappier)
            setChatsToPublicFlag = false;
        }

        switch (currentRegionID) {
            case TOA_LOBBY_REGION_ID:
                //Couldn't find any ScriptID/Varbit/Varp/VarCString that updated when the text from the toa party interface updates (besides script 6612 and 6613 / varbit 14345 changing the No party/Party/Step inside now! header when first joining a party). Let's check if the widget is not null and not hidden every game tick when inside the toa lobby aka region id 13454.
                processToAPartyInterface();
                break;
            case COX_BANK_REGION_ID:
                //There is no interface/widget/varc I could find. Also, nothing I could find that runs when a user gets added to the party here.
                addCoXBankPlayers();
                break;
        }

        if (getRLPartyMembersFlag > 0) { //Flag so party getMembers is not empty
            getRLPartyMembersFlag--; //Method can set int to 0 so -- first
            addPartyMembers();
        }

        if (getRLPartyUserJoinedMembersFlag > 0) { //Flag because memberJoined displayname is not immediately available.
            getRLPartyUserJoinedMembersFlag--; //Method can set int to 0 so -- first
            addUserJoinedPartyMembers();
        }

        if (shouldRefreshChat) {
            //Refresh chat in case someone got added to the list. Using a flag and doing it onGameTick so it doesn't potentially proc multiple time per gameTick, e.g. when joining an FC/CC
            client.refreshChat();
            shouldRefreshChat = false; //Reset flag
        }

        previousRegionID = currentRegionID;
        wasInFilteredRegion = isInFilteredRegion;
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired scriptPostFired) {
        switch (scriptPostFired.getScriptId()) {
            case REDRAW_CHAT_BUTTONS_SCRIPTID:
                //178 = [proc,redraw_chat_buttons]
                //Set the WidgetText for enabled chats to Custom
                setChatStoneWidgetTextAll();
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
                //todo: this todo is quite old and might be incorrect, but might still contain some useful info. add code with switch based on currentChatTabAlertTab, has to take the advanced config boolean into account, has to take the filterTriggered boolean into account and has to take the own name thingy and it's advanced config setting into account!
                break;
        }
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired scriptPreFired) {
        if (scriptPreFired.getScriptId() == CHAT_ALERT_ENABLE_SCRIPTID) {
            //Set the VarcInt that determines flashing/solid/no chat alert before it goes off. This way you can potentially set it to this value in PostScriptFired if a chat message was filtered.
            final int currentChatTabAlertTab = client.getIntStack()[0]; //1 = game. 2 = public. 3 = friends but does not show up when private is split (which is good, because the tab does also not flash then!). 4 = fc. 5 = cc. 6 = trade.
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
                    //tradeVarcIntCountdownValue = client.getVarcIntValue(TRADE_VARC_INT_COUNTDOWN_ID); //todo: fix this, probs with the solution in calendar note that you thought of months ago. Probs want to keep the notes around though.
                    break;
            }
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        //todo: potentially remove if switching to other alert solution -> probs remove most/all of it but keep some notes aroudn somewhere
        //The order is: ChatMessage => ScriptPreFired 180 => ScriptPostFired 180. However, sometimes it might be CM => CM => PreF => PostF => PreF => PostF. See "docs/testing/ChatMessage ScriptPrePostFired" for more info.
        final ChatMessageType chatMessageType = chatMessage.getType();
        if (!isChatTabFilterEnabled(chatMessageType)) {
            //if chat is not filtered, return
            //shouldFilter already has a Strings.isNullOrEmpty(playerName) check
            return;
        }

        final String playerName = chatMessage.getName();
        final Set<ChatTabFilter> chatTabFilters = getChatTabFilters(chatMessageType);
        final boolean shouldFilter = chatTabFilters == publicChatFilters ? shouldFilterMessagePublicChatMessage(playerName) : shouldFilterMessage(chatTabFilters, playerName);
        final boolean ownMessage = playerName.equals(client.getLocalPlayer().getName());

        //todo: potentially revert this to the old approach, not using a custom class
        //ChatTabAlert alert = new ChatTabAlert(shouldFilter, ownMessage);
        switch (chatMessageType) {
            //AUTOTYPER	is filtered on public = on anyway
            case PUBLICCHAT:
            case MODCHAT:
                //alert.setChatTabNumber(2);
                break;
            case PRIVATECHAT:
            case MODPRIVATECHAT:
                //alert.setChatTabNumber(3);
                break;
            case FRIENDSCHAT:
                //alert.setChatTabNumber(4);
                break;
            case CLAN_CHAT:
            case CLAN_GIM_CHAT:
            case CLAN_GUEST_CHAT:
                //alert.setChatTabNumber(5);
                break;
            case TRADEREQ:
                //TRADE and TRADE_SENT are not received when someone tries to trade you, only TRADEREQ
                //alert.setChatTabNumber(6);
                break;
        }

        //todo: check if this also works for the chatfilter plugin with nothing filtered here (so if this fixes both chatfilter and chat filter extended).
        //todo: If so, either make it an advanced config option to only work when the chat is filtered by chat filter extended (via dropdown: always, only chat filter extended messages, never)
        //todo: and also edit the advanced config descriptions of the already existing options + the readme
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged varbitChanged) {
        final int varbitId = varbitChanged.getVarbitId();
        //For some reason the channel and clan tab also have Varbits attached to them, while public, private, and trade do not (see script 184/185).
        //The varbit just sometimes gets transmitted, e.g. when leaving ToB, when hopping worlds, or when changing e.g. the FC filter (this also transmits the CC varbit).
        //Script 152 or 184 does not run then, so no use doing onScriptPrefired and then using Client.setVarbit
        if ((channelFilterEnabled && varbitId == FC_CHAT_FILTER_VARBIT && varbitChanged.getValue() != 0) || //928 = FC value, 929 = cc value (int $chatfilter1). 0 = show all IIRC.
                (clanFilterEnabled && varbitId == CC_CHAT_FILTER_VARBIT && varbitChanged.getValue() != 0)) {
            setChatsToPublic();
        }

        //Set inCoXRaidOrLobby when entering/leaving CoX (including lobby)
        if (varbitId == Varbits.IN_RAID) {
            inCoXRaidOrLobby = varbitChanged.getValue() > 0; //Convert the int to boolean. 0 = false, 1 = true.
            if (inCoXRaidOrLobby) {
                addCoXPlayers(); //playerSpawned procs before the varbit is set when joining a CoX lobby, so gotta also run this once when the varbit changes.
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
        setAddPartyMembersFlag(); //The method that gets called by this method already checks if user is in party
    }

    @Subscribe(priority = -2) //Run after any other core party code (PartyService & PartyPlugin)
    public void onUserJoin(UserJoin userJoin) {
        //Also runs for every person in the party when joining, so can potentially skip the PartyChanged code.
        //Specifically opted to use this approach instead of partyService.isInParty() && partyService.getMemberByDisplayName(player.getName()) != null
        //Usernames will persist till hopping/logout, even if the local player or a partyMember leaves the party
        final long memberId = userJoin.getMemberId();
        final String standardizedUsername = Text.standardize(partyService.getMemberById(memberId).getDisplayName());
        if (!Strings.isNullOrEmpty(standardizedUsername)) {
            if (runelitePartyStandardizedUsernames.add(standardizedUsername)) { //Not Combined with if statement above so else can be used
                shouldRefreshChat = true;
            }
        } else { //In case the party service can't get the display name yet, add memberId to HashSet and retry for 5 gameticks.
            partyMemberIds.add(memberId);
            setAddUserJoinedPartyMembersFlag();
        }
    }

    @Subscribe
    public void onPlayerSpawned(PlayerSpawned playerSpawned) {
        //Processing the widget inside cox does not work because the data is not transferred if the interface is not opened... Additionally, there is no widget when outside the raid and there is no interesting scriptId that runs while the player is outside.
        //Varbits.IN_RAID gets updated to 1 when joining the CoX underground lobby! When leaving the underground lobby, it gets set back to 0. Thus, if it's 1, the player is in the underground lobby or doing a CoX raid. isInFC check is not required because people have to be in the raiding party when this varbit is 1 (CoX is instanced).
        //Varbit gets set after PlayerSpawned fired, so addCoXPlayers is also called when the varbit changes.
        if (inCoXRaidOrLobby //If Varbits.IN_RAID > 0
                && raidPartyStandardizedUsernames.add(Text.standardize(playerSpawned.getPlayer().getName()))) { //Standardize playername that joined cox lobby / cox raid and add to HashSet.
            shouldRefreshChat = true;
        }
    }

    private void setRSProfileConfigFirstStart() {
        //Config keys that are not part of ChatFilterExtendedConfig are still empty on first startup. Prevent them being null by setting them before other code checks the config keys.
        //Alternatively add them to ChatFilterExtendedConfig but use hidden = true
        //The same goes for RSProfile keys. Currently all config keys that used to not be in the Config itself, have been moved to RSProfile

        if (configManager.getRSProfileKey() == null) {
            //The RSProfile has not been loaded yet. This happens when starting the plugin before the first login.
            //Alternatively you could probably also check if client.getAccountHash == -1
            return;
        }

        //Set defaults in case they don't exist yet
        //FilterEnabled booleans
        for (ChatTab chatTab : ChatTab.values()) {
            setRSProfileConfigDefault(chatTab.getFilterEnabledKeyName(), false);
        }

        final Set<ChatTabFilter> publicOHChatFiltersDefault = EnumSet.noneOf(ChatTabFilter.class); //Empty set since otherwise the default is only showing OH (overhead text) instead of also chatbox text.
        final Set<ChatTabFilter> publicChatFiltersDefault = EnumSet.allOf(ChatTabFilter.class);
        publicChatFiltersDefault.remove(ChatTabFilter.PUBLIC);
        final Set<ChatTabFilter> privateChatFiltersDefault = EnumSet.noneOf(ChatTabFilter.class); //Empty set since forcePrivateOn is disabled by default anyway
        final Set<ChatTabFilter> channelChatFiltersDefault = EnumSet.allOf(ChatTabFilter.class);
        channelChatFiltersDefault.remove(ChatTabFilter.PUBLIC);
        final Set<ChatTabFilter> clanChatFiltersDefault = EnumSet.allOf(ChatTabFilter.class);
        clanChatFiltersDefault.remove(ChatTabFilter.PUBLIC);
        final Set<ChatTabFilter> tradeChatFiltersDefault = EnumSet.of(ChatTabFilter.FRIENDS, ChatTabFilter.CC, ChatTabFilter.GUEST_CC, ChatTabFilter.WHITELIST);
        //Public is randoms, FCs are often open, raid party applying is easy, RL party can be joined freely if you have the pass
        //You have to add friends; cc & guest cc have guests disabled by default; custom whitelist has to be set manually

        //Set defaults for the chat sets. Convert to JSON because trying to cast either [] or e.g. ["FRIENDS","FC","CC","GUEST_CC","RAID","PARTY","WHITELIST"] to Set.class will result in a ClassCastException
        setRSProfileConfigDefault(ChatTab.getPublicOHChatFiltersKeyName(), gson.toJson(publicOHChatFiltersDefault));
        setRSProfileConfigDefault(ChatTab.PUBLIC.getChatTabFiltersKeyName(), gson.toJson(publicChatFiltersDefault));
        setRSProfileConfigDefault(ChatTab.PRIVATE.getChatTabFiltersKeyName(), gson.toJson(privateChatFiltersDefault));
        setRSProfileConfigDefault(ChatTab.CHANNEL.getChatTabFiltersKeyName(), gson.toJson(channelChatFiltersDefault));
        setRSProfileConfigDefault(ChatTab.CLAN.getChatTabFiltersKeyName(), gson.toJson(clanChatFiltersDefault));
        setRSProfileConfigDefault(ChatTab.TRADE.getChatTabFiltersKeyName(), gson.toJson(tradeChatFiltersDefault));
        //todo: add other rsprofile configs to this if you decide to add more rsprofile values

        areRSProfileDefaultsSet = true; //All the code above procs onConfigChanged -> runs updateConfig(). You don't want to run (part of) updateConfig() before all defaults have been set to prevent NPEs.
    }

    private void setRSProfileConfigDefault(String keyName, Object value) {
        //Set the RSProfileConfiguration defaults if they don't exist yet
        if (configManager.getRSProfileConfiguration(CONFIG_GROUP, keyName) != null) {
            //RSProfile config has been set for this key, don't need to set defaults
            return;
        }
        configManager.setRSProfileConfiguration(CONFIG_GROUP, keyName, value);
    }

    private void convertSetToEnumSet(Set<ChatTabFilter> configSet, Set<ChatTabFilter> setToConvertTo) {
        //config.SetNameHere() returns a LinkedHashset, even if you set the default as e.g. return EnumSet.noneOf(EnumClassName.class)
        //Thus, clear the already created final EnumSet and add all the elements to it
        setToConvertTo.clear();
        setToConvertTo.addAll(configSet);
    }

    /*
    Alternatively make set not final and then use this, but I dislike that the implementation of the set can change to another type of set if I'm not careful
    //copyOf tries to get the type of enum from the value, but it can't if the set is empty
    private EnumSet<ChatTabFilter> getEnumSet(Set<ChatTabFilter> set) {
        if (set.isEmpty()) {
            return EnumSet.noneOf(ChatTabFilter.class);
        }
        return EnumSet.copyOf(set);
    }
     */

    private void convertCommaSeparatedStringToSet(String configString, Set<String> setToConvertTo) {
        //Convert a CSV config string to a set
        setToConvertTo.clear();
        //standardize: removes tags, replace nbsp with space, made lower case, trims technically (but not split yet, so done later)
        // -> replaceAll("\\R", "") -> remove all unicode linebreak sequences (see https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html). replaceAll is used instead of replace since replaceAll uses regex as input instead of just a regular string.
        // -> fromCSV: splits on commas, omits empty strings, trims results
        setToConvertTo.addAll(Text.fromCSV(Text.standardize(configString).replaceAll("\\R", "")));
    }

    private Set<ChatTabFilter> getChatTabFiltersRSProfile(String keyName) {
        //Get the ChatTabFilters from the RSProfile. Use gson to get it from json. Without gson, you get a ClassCastException.
        //Don't need to check if RSProfile is already loaded since this method is only called from locations with a loaded RSProfile
        //PM In newer versions of Gson .getType() can very likely be removed.
        return gson.fromJson(configManager.getRSProfileConfiguration(CONFIG_GROUP, keyName), new TypeToken<Set<ChatTabFilter>>() {}.getType());
    }

    private String getFilteredRegionsJson() {
        //toJson filteredRegions, then remove quotation marks from everything
        // (aka FilteredRegionField.serializedName, FilteredRegion's fields' values, AND regionid)
        // to make the String even less JSON compliant and even more brittle! (to save some space lol)
        //PM if for some reason this is too many characters for a single config key, which I don't expect it to ever be,
        // just split it into multiple config keys (e.g. per regionID) with a prefix like loottracker does
        return gson.toJson(filteredRegions).replace("\"", "");
    }

    private void convertStringToFilteredRegions(String configString) {
        //Convert the (config) string to FilteredRegions
        //It's kinda double when using the MenuEntries because it does: change map -> convert to String -> set config ->
        // ConfigChanged fires -> clear map -> get String from config -> put on map. But oh well...

        //Clear the old filteredRegions map before adding the new regions
        filteredRegions.clear();

        //Remove all whitespace characters so we don't get problems with people adding incorrect whitespaces
        configString = configString.replaceAll("\\p{javaSpaceChar}", ""); //Removes all isSpaceChar(int) characters -> tab, new line, space, NBSP (\s+ very likely does not replace NBSP)

        try {
            //Convert String back to Map<Integer, FilteredRegion> and putAll those values to filteredRegions
            filteredRegions.putAll(gson.fromJson(configString, new TypeToken<Map<Integer, FilteredRegion>>() {}.getType()));
        } catch (JsonParseException jsonParseException) {
            //Catch exceptions so the plugin also starts when the user has fucked up the Json for some reason
            setFilteredRegionsMalformedString("Error when parsing FilteredRegion data. Likely the String is malformed, and no FilteredRegions have been loaded. Exception: " +jsonParseException.getMessage());
        }

        //Technically, empty FilteredRegions (all default values) might have been added if the problem was small enough that FilteredRegionAdapter just skipped the value
        removeEmptyFilteredRegionsEntries();
    }

    private void removeEmptyFilteredRegionsEntries() {
        //Removes all FilteredRegions with default values for its fields from the FilteredRegions Map (excluding PublicOHChatSet)

        //If I'd want to check for equality between a FilteredRegion and another FilteredRegion, such as one with all default values,
        // (checking if the values for the fields are equal), I'd have to override the equals() method.
        //By default, equals() implementation compares object memory addresses, so it works the same as the == operator
        //If I'd override equals, I'd also have to override hashCode: "You must override hashCode() in every class that
        // overrides equals(). Failure to do so will result in a violation of the general contract for Object.hashCode(),
        // which will prevent your class from functioning properly in conjunction with all hash-based collections,
        // including HashMap, HashSet, and Hashtable." - https://stackoverflow.com/questions/2265503/why-do-i-need-to-override-the-equals-and-hashcode-methods-in-java
        //Objects.equals and Objects.deepEquals (expectedly) did not work. Thus, we do this crap.
        filteredRegions.values().removeIf(region ->
                !region.isPublicChatCustomOnly() && region.getPublicChatSet().isEmpty()
                        && !region.isPrivateChatCustomOnly() && region.getPrivateChatSet().isEmpty()
                        && !region.isChannelChatCustomOnly() && region.getChannelChatSet().isEmpty()
                        && !region.isClanChatCustomOnly() && region.getClanChatSet().isEmpty()
                        && !region.isTradeChatCustomOnly() && region.getTradeChatSet().isEmpty());
        //if the non-OH set is empty, custom chat is disabled. Thus, sets with only !region.getPublicOHChatSet().isEmpty()
        // are invalid for a FilteredRegion -> that's why there's no && region.getPublicOHChatSet().isEmpty() check above
    }

    private void getCoXVarbit() {
        //Converts the IN_RAID varbitvalue to boolean. Useful in case the plugin starts with the player already logged in.
        final GameState gameState = client.getGameState();
        if (gameState != GameState.LOGGED_IN && gameState != GameState.LOADING) {
            //Not logged in, return
            return;
        }
        inCoXRaidOrLobby = client.getVarbitValue(Varbits.IN_RAID) > 0; //Convert the int to boolean. 0 = false, 1 = true.
    }

    private void addCoXPlayers() {
        //Get all players when inside CoX lobby or CoX raid and add them to the HashSet after standardizing the name
        //Useful when starting the plugin inside CoX or when clearing the raid party inside CoX (doesn't proc PlayerSpawned)
        if (!inCoXRaidOrLobby
                || (client.getGameState() != GameState.LOGGED_IN && client.getGameState() != GameState.LOADING)) {
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
    private void addToBPlayers() {
        //Adds the ToB players to the Raid HashSet. Useful when resetting the list and updating it again so the old ToA players don't join.
        //The varcs do not get cleared if the player leaves, so check if the player is inside tob first.
        //However, when joining a new raid, the varcStrings get updated. E.g. first do a raid with 4 people, then a duo tob => upon entering, player 3 and 4 strings will be emptied.
        if (client.getVarbitValue(Varbits.THEATRE_OF_BLOOD) > 1) {
            for (int i = 0; i < 5; i++) {
                addInRaidUsernamesVarClientStr(TOB_IN_RAID_VARCSTR_PLAYER1_INDEX + i);
            }
        }
    }

    private void addToAPlayers() {
        //Adds the ToA players to the Raid HashSet. Useful when resetting the list and updating it again so the old ToB players don't join.
        //The varcs do not get cleared if the player leaves, so check if the player is inside ToA first.
        //However, when joining a new raid, the varcStrings get updated. E.g. first do a raid with 4 people, then a duo ToA => upon entering, player 3 and 4 strings will be emptied.
        //For an explanation about the varps, check the comment when they are declared at the top
        if (client.getVarpValue(IN_A_RAID_VARPID) > 0 && client.getVarpValue(TOA_PARTY_VARPID) > -1) {
            for (int i = 0; i < 8; i++) {
                addInRaidUsernamesVarClientStr(TOA_IN_RAID_VARCSTR_PLAYER1_INDEX + i);
            }
        }
    }

    private void addFCMembers() {
        //To add all the FC members to the HashSet. Useful if the plugin gets enabled while already in an FC, so should run in StartUp.
        final FriendsChatManager friendsChatManager = client.getFriendsChatManager();
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

    private void addCCMembers() {
        //To add all the CC/GIM members to the HashSet. Useful if the plugin gets enabled while already in a CC, so should run in StartUp.
        //Add own CC
        final ClanChannel clanChannel = client.getClanChannel();
        final ClanSettings clanSettings = client.getClanSettings(ClanID.CLAN);
        addClanMembers(clanChannel, clanSettings);

        //Add GIM
        final ClanChannel gimClanChannel = client.getClanChannel(ClanID.GROUP_IRONMAN);
        final ClanSettings gimSettings = client.getClanSettings(ClanID.GROUP_IRONMAN);
        addClanMembers(gimClanChannel, gimSettings);
        System.out.println(clanMembersStandardizedUsernames); //todo: remove
        System.out.println(clanTotalStandardizedUsernames); //todo: remove
    }

    private void addClanMembers(ClanChannel clanChannel, ClanSettings clanSettings) {
        //Add clan members to the appropriate sets
        addClanOrGuestClanMembers(clanChannel, clanSettings, clanMembersStandardizedUsernames, clanTotalStandardizedUsernames);
    }

    private void addGuestClanMembers(ClanChannel clanChannel, ClanSettings clanSettings) {
        //Add guest clan members to the appropriate sets
        addClanOrGuestClanMembers(clanChannel, clanSettings, guestClanMembersStandardizedUsernames, guestClanTotalStandardizedUsernames);
    }

    private void addClanOrGuestClanMembers(ClanChannel clanChannel, ClanSettings clanSettings, Set<String> clanMemberSet, Set<String> clanTotalSet) {
        if (clanSettings != null) {
            //Adds all the members to the HashSet (according to the clan settings)
            for (ClanMember clanMember : clanSettings.getMembers()) {
                if (clanMemberSet.add(Text.standardize(clanMember.getName()))) {
                    shouldRefreshChat = true;
                }
            }
            //All clan members have been added to the clanMembersSet based on the clan settings
            //Also add them to the clanTotalSet, which later on adds all the guests as well
            //While the chance is almost 0, technically the onGameTick refresh called for above could have happened
            //before addAll has been completed while the code below will not set the flag to true in very specific
            //scenarios. If the flag gets set twice, then whatever because it'll only be executed once.
            if (clanTotalSet.addAll(clanMemberSet)) {
                shouldRefreshChat = true;
            }
        }

        //Clan members get added via the clan settings, but clan/guest clan guests are not a part of those.
        if (clanChannel != null) {
            //Previous solution does not add the guests that are already in the CC, also add those
            for (ClanChannelMember clanMember : clanChannel.getMembers()) {
                if (clanTotalSet.add(Text.standardize(clanMember.getName()))) {
                    shouldRefreshChat = true;
                }
            }
        }
    }

    private void addGuestCCMembers() {
        //To add all the guest CC members to the HashSet. Useful if the plugin gets enabled while already in a guest CC, so should run in StartUp.
        //Add guest CC
        final ClanChannel guestClanChannel = client.getGuestClanChannel();
        final ClanSettings guestClanSettings = client.getGuestClanSettings();
        addGuestClanMembers(guestClanChannel, guestClanSettings);
        System.out.println(guestClanMembersStandardizedUsernames); //todo: remove
        System.out.println(guestClanTotalStandardizedUsernames); //todo: remove
    }

    @Nullable
    private Set<ChatTabFilter> getChatTabFilters(int componentID) {
        //Returns the Set<ChatTabFilter> based on the componentID. Originally had it in an Object with also OH, but it's kind of annoying to use so screw that.
        //Returns null when componentID != chatstone componentID
        //Alternatively just getChatTab enum element -> return set you put in the enum
        switch (componentID) {
            case ComponentID.CHATBOX_TAB_PUBLIC:
                return publicChatFilters;
            case ComponentID.CHATBOX_TAB_PRIVATE:
                return privateChatFilters;
            case ComponentID.CHATBOX_TAB_CHANNEL:
                return channelChatFilters;
            case ComponentID.CHATBOX_TAB_CLAN:
                return clanChatFilters;
            case ComponentID.CHATBOX_TAB_TRADE:
                return tradeChatFilters;
        }
        return null;
    }

    @Nullable
    private Set<ChatTabFilter> getOHChatTabFilters(int componentID) {
        //Returns the OHChatTabFilters based on the componentID. Originally had it in an Object with also 3D/regular, but it's kind of annoying to use so screw that.
        //Returns null when componentID != public chatstone componentID
        if (componentID == ComponentID.CHATBOX_TAB_PUBLIC) {
            return publicOHChatFilters;
        }
        return null;
    }

    private boolean shouldFilterChatType(int componentID) {
        //Should a chatstone (e.g. private) be filtered based on the componentID and the config set
        //Does not check if the filter is currently active (i.e. if it's on custom)
        final Set<ChatTabFilter> chatTabFilters = getChatTabFilters(componentID);
        //getChatTabFilterSet already checks the componentID, so we don't have to check if it's a chatstone componentID besides doing a null check
        //The publicOH filter only works when the normal one is also active, so can ignore the OH one for now.
        if (chatTabFilters != null) {
            return !chatTabFilters.isEmpty();
        }
        return false;
    }

    private void redrawChatButtons() {
        //Run [proc,redraw_chat_buttons]
        final GameState gameState = client.getGameState();
        if (gameState != GameState.LOGGED_IN && gameState != GameState.LOADING) {
            //not logged in
            return;
        }

        clientThread.invokeLater(() -> {
            client.runScript(REDRAW_CHAT_BUTTONS_SCRIPTID); //[proc,redraw_chat_buttons]
        });
    }

    private void disableFilterWhenSetEmptied() {
        //Disable currently active filter + rebuild chatbuttons if all filters for a chat tab get disabled in config
        //Called in onConfigChanged
        boolean shouldRedraw = false;
        //Update arrays to most recent values
        final boolean[] filtersEnabled = new boolean[]{publicFilterEnabled, privateFilterEnabled, channelFilterEnabled, clanFilterEnabled, tradeFilterEnabled}; //If you need to use this somewhere else, try making it private static and updating it in onConfigChanged
        //Iterate through all chat filter enabled booleans and check if they should be active according to the config or not
        for (int i = 0; i < filtersEnabled.length; i++) {
            if (filtersEnabled[i] && !shouldFilterChatType(CHATBOX_COMPONENT_IDS.get(i))) {
                setChatFilterConfig(CHATBOX_COMPONENT_IDS.get(i), false);
                shouldRedraw = true;
            }
        }
        //If a chat filter has been disabled because the config set has been emptied, redraw all chat buttons, then set the Custom text again for all active ones
        if (shouldRedraw) {
            redrawChatButtons(); //setChatStoneWidgetTextAll() is not required because that already procs when REDRAW_CHAT_BUTTONS_SCRIPTID is procced
        }
    }

    private void clearRaidPartySet() {
        //Clears the raid party sets. Also clears the string so the plugin will process the party interface if needed
        previousRaidPartyInterfaceText = "";
        raidPartyStandardizedUsernames.clear();
        shouldRefreshChat = true;
    }

    private void updateFilteredRegions() {
        convertStringToFilteredRegions(config.filteredRegionsData());
        previousRegionID = 0; //Set to 0 so it triggers a recheck in GameTick, in case the region is not filtered anymore/is now filtered/specific filter changed
    }

    private void clearRaidPartySetManually(MenuEntry menuEntry) {
        //Yes, MenuEntry menuEntry is required here for it to work afaik
        //Clears the raid party set and also rebuilds it if it's applicable (e.g. when inside tob)
        clearRaidPartySet();
        //Rebuild the raid party HashSet by adding the current people to it. Events that run every gametick (either via onGameTick or e.g. via a script that runs every gametick, are excluded here since they'll run anyway).
        //Thus, CoX bank is excluded, ToB/ToA lobby party interface is excluded.
        addCoXPlayers(); //Get CoX players because it does not trigger onPlayerSpawned while inside a raid.
        processToBBoard(); //Person might close the interface before the script procs.
        processToABoard(); //Person might close the interface before the script procs.
        addToBPlayers(); //Checks if player is inside ToB to only add them then. Use addAllInRaidUsernamesVarClientStr() if you also want to add when outside ToB or old ToA players
        addToAPlayers(); //Checks if player is inside ToA to only add them then. Use addAllInRaidUsernamesVarClientStr() if you also want to add when outside ToA or old ToB players
        client.refreshChat(); //Refresh chat after manually changing the raid filter set
        actuallySendMessage(getColoredPluginName() + "The Raid Party members set has been cleared.");
    }

    //Get the plugin name wrapped in the appropriate color tags, [], and with a space behind it to use in chat messages
    private String getColoredPluginName() {
        return "[" + getColorWrappedString("Chat Filter Extended plugin") + "] ";
    }

    //Get the string wrapped in the appropriate color tags to use in chat messages
    //Value could be inlined, but decided not to in this case so I can also easily use it for other stuff in the future
    @SuppressWarnings("SameParameterValue")
    private String getColorWrappedString(String stringToWrap) {
        //Get the opaque color from chat color plugin or ingame color
        Color color = MoreObjects.firstNonNull(configManager.getConfiguration(CHAT_COLOR_CONFIG_GROUP, "opaqueFriendsChatChannelName", Color.class), JagexColors.CHAT_FC_NAME_OPAQUE_BACKGROUND);
        if (client.isResized() && client.getVarbitValue(Varbits.TRANSPARENT_CHATBOX) == 1) {
            //Replace color if using transparent chatbox
            color = MoreObjects.firstNonNull(configManager.getConfiguration(CHAT_COLOR_CONFIG_GROUP, "transparentFriendsChatChannelName", Color.class), JagexColors.CHAT_FC_NAME_TRANSPARENT_BACKGROUND);
        }
        //Wrap string in color tags and return the value
        return ColorUtil.wrapWithColorTag(stringToWrap, color);
    }

    private boolean isChatStone(int componentID) {
        //Check if the componentID is the componentID of a chatstone
        return ChatTab.getEnumElement(componentID) != null;
    }

    private void enableChatFilter(int componentID) {
        //Enables the chat filter for the specific tab is the users selects the appropriate menu option.
        setChatFilterConfig(componentID, true);
        setChatsToPublic();
    }

    private void setChatFilterConfig(int componentID, boolean enableFilter) {
        //Set the RSProfile config value for a chat based on the componentID. Boolean enableFilter: enable or disable a filter
        //publicFilterEnabled = enableFilter is not necessary since ConfigManager does trigger updateConfig() if the config value actually gets changed from false to true or vice versa
        //Alternatively use a switch (componentID) statement like you did before.
        final ChatTab chatTab = ChatTab.getEnumElement(componentID);
        if (chatTab == null) {
            //Not the ComponentID of a ChatTab
            return;
        }

        configManager.setRSProfileConfiguration(CONFIG_GROUP, chatTab.getFilterEnabledKeyName(), enableFilter);
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

        //Public, private, trade remember between hops.
        //Channel, Clan don't remember between hopping; potentially related to varbits as described here https://discord.com/channels/301497432909414422/301497432909414422/1086022946633547867 (i.e. I suspect when hopping it reads the state from the varbits and then sets the chat filters according to those values)
        clientThread.invoke(() -> {
            //todo: make this into an enum or private static final ints probs with the 2-6 and the 0. Also make client.runScript(CHAT_SET_FILTER_SCRIPTID, x, x) a separate method probs so you can easily call it when setting from filteredRegion back to unfilteredRegion?
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
        final GameState gameState = client.getGameState();
        if (gameState != GameState.LOGGED_IN && gameState != GameState.LOADING) {
            return;
        }

        for (int componentID : CHATBOX_COMPONENT_IDS) {
            setChatStoneWidgetText(componentID);
        }
    }

    private void setChatStoneWidgetText(int componentID) {
        //Sets the WidgetText for the specific chat to Custom, based on componentID. Usage of this already has GameState check.
        final Widget chatWidget = client.getWidget(componentID);
        if (chatWidget == null || !isChatFiltered(componentID)) {
            //chatWidget is null or the chat is not filtered -> don't need to set custom text
            return;
        }

        chatWidget.getStaticChildren()[2].setText(TAB_CUSTOM_TEXT_STRING); //or e.g. chatWidget.getStaticChildren().length-1 but that might change more often idk
    }

    private boolean isChatFiltered(int componentID) {
        //Returns true if the chat is filtered based on ComponentId.
        //Alternatively just getChatTab enum element -> return boolean you put in the enum
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

    private boolean isChatTabFilteredRegion(int regionID, int componentID) {
        //Is the current chat tab (e.g. private) filtered for the specific region?
        final ChatTab chatTab = ChatTab.getEnumElement(componentID);
        if (chatTab == null) {
            //if componentID is not actually from a chattab, return false
            return false;
        }

        final FilteredRegion filteredRegion = filteredRegions.get(regionID);
        if (filteredRegion == null) {
            //This region is not a filtered region, based on the regionID
            return false;
        }

        switch (chatTab) {
            case PUBLIC:
                return isRegionFilteredForChat(filteredRegion.isPublicChatCustomOnly(), filteredRegion.getPublicChatSet());
            case PRIVATE:
                return isRegionFilteredForChat(filteredRegion.isPrivateChatCustomOnly(), filteredRegion.getPrivateChatSet());
            case CHANNEL:
                return isRegionFilteredForChat(filteredRegion.isChannelChatCustomOnly(), filteredRegion.getChannelChatSet());
            case CLAN:
                return isRegionFilteredForChat(filteredRegion.isClanChatCustomOnly(), filteredRegion.getClanChatSet());
            case TRADE:
                return isRegionFilteredForChat(filteredRegion.isTradeChatCustomOnly(), filteredRegion.getTradeChatSet());
        }
        return false;
    }

    private boolean isRegionFilteredForChat(boolean justCustom, Set<ChatTabFilter> chatTabFilters) {
        //Is the specific region auto-enabled for some chat? To be used in switch statement
        //Check if justCustom is true or chatTabFilters is not empty
        //Does not have to check OH because it does not allow only setting OHSet since then the custom filter is not active
        return justCustom || !chatTabFilters.isEmpty();
    }

    private void addFilteredRegionValue(int regionID, int componentID) {
        //Adds the regionID + ChatTab combination to the appropriate FilteredRegion field (with justCustom == true)
        addFilteredRegionValue(regionID, componentID, null, null);
    }

    private void addFilteredRegionValue(int regionID, int componentID, Set<ChatTabFilter> chatTabFilters, Set<ChatTabFilter> OHChatTabFilters) {
        //Set ChatTabFilters for the appropriate field(s) (based on ChatTab) of a specific FilteredRegion (based on regionID)
        //Use the other (overloaded) method for a justCustom boolean

        final ChatTab chatTab = ChatTab.getEnumElement(componentID);
        if (chatTab == null) {
            //if componentID is not actually from a ChatTab, return
            return;
        }

        //String to be used in chat message at the end. If !justCustom, this String will be set to something else below
        String chatsAdded = "Custom";

        if (chatTabFilters == null) { //Aka if justCustom
            setFilteredRegionField(regionID, chatTab, true); //set customOnly to true, ChatTabFilters to empty
        } else { //if (!justCustom)
            setFilteredRegionField(regionID, chatTab, chatTabFilters, OHChatTabFilters); //set ChatTabFilters, customOnly to false

            final StringBuilder chatsAddedBuilder = new StringBuilder(); //Purely used for the ingame chat message
            for (ChatTabFilter chatTabFilter : chatTabFilters) {
                chatsAddedBuilder.append(chatTabFilter.getAbbreviation()).append("/"); //Tbh can probably also just use String += since it will never loop over a lot of elements, but rightfully IntelliJ complains about doing String concatenation in a loop, so let's use a StringBuilder
            }

            //Convert StringBuilder to String so I can use replace. Should have fixed this in another way but w.e.
            chatsAdded = chatsAddedBuilder.toString();

            if (OHChatTabFilters != null) { //Aka if ChatTab = public, otherwise OHChatTabFilters would be null! Thus, no componentID/ChatTab check is needed!
                for (ChatTabFilter OHChatTabFilter : OHChatTabFilters) {
                    chatsAdded = chatsAdded.replace(OHChatTabFilter.getAbbreviation(), OHChatTabFilter.getOHAbbreviation()); //Unlike in MenuEntryAdded, no slash is added. Omitting the slash here does not result in: "Public OH: Show Public OH/Friends/CC OH
                }
            }

            chatsAdded = chatsAdded.substring(0, chatsAdded.length() - 1); //Remove the trailing space
        }

        configManager.setConfiguration(CONFIG_GROUP, "filteredRegionsData", getFilteredRegionsJson());
        //This message cannot be procced while logged out, so don't need to check gamestate and/or set a flag
        actuallySendMessage(getColoredPluginName() + "Region " + regionID + " has been added to the filtered regions string for the " + chatTab + " chat tab (" + chatsAdded + ").");
    }

    private void setFilteredRegionField(int regionID, ChatTab chatTab, boolean justCustom) {
        //Set the fields for a specific ChatTab for a specific FilteredRegion to these values.
        // At the end remove the FilteredRegion from the FilteredRegions Map if it's all default values.
        //This is for setting the boolean. The corresponding ChatTabFilter Sets will be set to empty (default).
        setFilteredRegionField(regionID, chatTab, justCustom, null, null);
    }

    private void setFilteredRegionField(int regionID, ChatTab chatTab, Set<ChatTabFilter> chatTabFilters, Set<ChatTabFilter> OHChatTabFilters) {
        //Set the fields for a specific ChatTab for a specific FilteredRegion to these values.
        // At the end remove the FilteredRegion from the FilteredRegions Map if it's all default values.
        //This is for setting the ChatTabFilter Sets. The corresponding boolean will be set to false (default).
        setFilteredRegionField(regionID, chatTab, false, chatTabFilters, OHChatTabFilters);
    }

    private void setFilteredRegionField(int regionID, ChatTab chatTab, boolean justCustom, Set<ChatTabFilter> chatTabFilters, Set<ChatTabFilter> OHChatTabFilters) {
        //Set the fields for a specific ChatTab for a specific FilteredRegion to these values.
        // At the end remove the FilteredRegion from the FilteredRegions Map if it's all default values.
        //It's recommended to use the other (overloaded) methods, not this method!

        //Get FilteredRegion from Map, or create it if it does not exist
        final FilteredRegion filteredRegion = MoreObjects.firstNonNull(filteredRegions.get(regionID), new FilteredRegion());

        if (justCustom || chatTabFilters == null) {
            //if justCustom, ChatTabFilters should be empty
            //Additionally, you should never be able to have a FilteredRegion with an empty publicChatSet but a non-empty publicOHChatSet
            chatTabFilters = EnumSet.noneOf(ChatTabFilter.class);
            OHChatTabFilters = EnumSet.noneOf(ChatTabFilter.class);
        }

        //OHChatTabFilters = MoreObjects.firstNonNull(OHChatTabFilters, EnumSet.noneOf(ChatTabFilter.class)); is not required!
        //It'd result in an empty enumSet in case chatTabFilters is not null, but OHChatTabFilters is. This happens for all ChatTabs except ChatTab.PUBLIC
        //However, those ChatTabs do not result in filteredRegion.setPublicOHChatSet(OHChatTabFilters) being called in the switch statement below.

        //Set the fields for the specific chatTab for the filteredRegion to the requested values
        switch (chatTab) {
            case PUBLIC:
                filteredRegion.setPublicChatCustomOnly(justCustom);
                filteredRegion.setPublicOHChatSet(OHChatTabFilters);
                filteredRegion.setPublicChatSet(chatTabFilters);
                break;
            case PRIVATE:
                filteredRegion.setPrivateChatCustomOnly(justCustom);
                filteredRegion.setPrivateChatSet(chatTabFilters);
                break;
            case CHANNEL:
                filteredRegion.setChannelChatCustomOnly(justCustom);
                filteredRegion.setChannelChatSet(chatTabFilters);
                break;
            case CLAN:
                filteredRegion.setClanChatCustomOnly(justCustom);
                filteredRegion.setClanChatSet(chatTabFilters);
                break;
            case TRADE:
                filteredRegion.setTradeChatCustomOnly(justCustom);
                filteredRegion.setTradeChatSet(chatTabFilters);
                break;
        }

        //Put regionID,FilteredRegion pair on FilteredRegions. If the map already contains the mapping for the specified key, the old value is replaced by the new specified value.
        filteredRegions.put(regionID, filteredRegion);

        //The region might not be filtered at this point, remove it from the map if that's the case
        //Alternatively, don't call this here, but in the code that removes a FilteredRegion chat filter via the MenuEntry.
        //It's potentially superfluous here, e.g. in case a FilteredRegion is just being updated, instead of setting the values back to default.
        //Opted for now to do it here because it seems to be less prone to breaking something if I change the current approach at some point.
        removeEmptyFilteredRegionsEntries();
    }

    private void removeFilteredRegionValue(int regionID, int componentID) {
        //Remove the regionID + chatTab combination from the FilteredRegions Map

        final ChatTab chatTab = ChatTab.getEnumElement(componentID);
        if (chatTab == null) {
            //if componentID is not actually from a ChatTab, return
            return;
        }

        setFilteredRegionField(regionID, chatTab, false); //Set the relevant values back to default (false, empty sets), and remove the FilteredRegion if it's empty
        configManager.setConfiguration(CONFIG_GROUP, "filteredRegionsData", getFilteredRegionsJson()); //set config value
        //This message cannot be procced while logged out, so don't need to check gamestate and/or set a flag
        actuallySendMessage(getColoredPluginName() + "Region " + regionID + " has been removed from the FilteredRegions data for the " + chatTab + " chat tab.");
    }

    private void actuallySendMessage(String message) {
        //If this can be procced while logged out, check for GameState.LOGGED_IN/LOADING before calling this and otherwise set a flag to be consumed in onGameTick
        //client.addChatMessage has to be called on clientThread -> invoking in case I want to use this on e.g. startUp in the future
        //Doesn't cause any error if not called on client.getGameState() == GameState.LOGGED_IN
        clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, ""));
    }

    //Get the StringBuilder for the submenus.
    //boolean should be set.contains(chatTabFilter) or OHSet.contains(chatTabFilter)
    //String should be chatTabFilter.getMenuName() or chatTabFilter.getOHMenuName()
    private StringBuilder getSubMenuAddRemoveStringBuilder(boolean setContainsChatTabFilter, String chatTabFilter) {
        final StringBuilder optionBuilder = new StringBuilder();
        if (setContainsChatTabFilter) { //Already in the set, so the option should be "Remove "
            optionBuilder.append("Remove ");
        } else {
            optionBuilder.append("Add "); //chatTabFilter is not in the active Set<ChatTabFilter> set yet, so option should start with "Add "
        }
        optionBuilder.append(chatTabFilter); //Add the value to the OptionBuilder so e.g. "Add Friends"
        return optionBuilder;
    }

    private void addRemoveValueFromChatSet(Set<ChatTabFilter> chatTabFilters, ChatTabFilter chatTabFilter, int componentID) {
        addRemoveValueFromChatSet(chatTabFilters, chatTabFilter, componentID, false);
    }

    private void addRemoveValueFromChatSet(Set<ChatTabFilter> chatTabFilters, ChatTabFilter chatTabFilter, int componentID, boolean OHSet) {
        //Add or remove a value from a chat set based on if the set already contains the value or not.
        //Used in the right click menu to add or remove a value from the set.
        //boolean OHSet = if it's the publicOH set -> true
        if (!chatTabFilters.add(chatTabFilter)) {
            //If the set does not contain the value, add it, return !true aka false, so don't remove it.
            //If the set does contain the value, it can't be added and the if statement is !false aka true, so it'll remove the value.
            chatTabFilters.remove(chatTabFilter);
        }

        //Get the config key name based on the componentID, or the PublicOH config key if it's the OHSet
        final String keyName = OHSet ? ChatTab.getPublicOHChatFiltersKeyName() : getChatTabFilterKeyName(componentID);
        if (keyName == null) {
            //Returns null when componentID != chatstone componentID
            return;
        }

        configManager.setRSProfileConfiguration(CONFIG_GROUP, keyName, gson.toJson(chatTabFilters)); //Use gson because you get a ClassCastException otherwise when you try to getRSProfileConfiguration
        //Potentially add a chat message when changing the chatTabFilters, but might get too spammy when adding/removing multiple values. One can already confirm it happened by just right-clicking on the chat tab and seeing "Show: Public/Friends/FC/CC" etc.
        if (keyName.equals(ChatTab.PRIVATE.getChatTabFiltersKeyName()) && !forcePrivateOn) {
            //Notification when people screw with the private filter without forcePrivateOn so they don't complain about it not working properly.
            actuallySendMessage(getColoredPluginName() + "<col=FF0000>Private filtering generally only works well when 'force private to on' is enabled in the plugin's config settings.");
        }
    }

    @Nullable
    private String getChatTabFilterKeyName(int componentID) {
        //Returns the ChatTabFilters keyname based on the componentID
        //Returns null when componentID != chatstone componentID
        final ChatTab chatTab = ChatTab.getEnumElement(componentID);
        if (chatTab == null) {
            return null;
        }
        return chatTab.getChatTabFiltersKeyName();
    }

    private void addRemoveValueFromOHChatSet(Set<ChatTabFilter> chatTabFilters, ChatTabFilter chatTabFilter) {
        addRemoveValueFromChatSet(chatTabFilters, chatTabFilter, ChatTab.PUBLIC.getComponentID(), true);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted") //This is true, but I like keeping it like this for my own logic
    private boolean isChatTabFilterEnabled(ChatMessageType chatMessageType) {
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
    private Set<ChatTabFilter> getChatTabFilters(ChatMessageType chatMessageType) {
        //Translates the ChatMessageType to the appropriate Set (not OH, so not for overheads).
        if (chatMessageType != null) {
            switch (chatMessageType) {
                //AUTOTYPER	is not shown/is filtered on public = on anyway
                case PUBLICCHAT:
                case MODCHAT:
                    return publicChatFilters;
                case PRIVATECHAT:
                case MODPRIVATECHAT:
                    return privateChatFilters;
                case FRIENDSCHAT:
                    return channelChatFilters;
                case CLAN_CHAT:
                case CLAN_GIM_CHAT:
                case CLAN_GUEST_CHAT:
                    return clanChatFilters;
                case TRADEREQ:
                    //TRADE and TRADE_SENT are not received when someone tries to trade you, only TRADEREQ
                    return tradeChatFilters;
            }
        }
        return null;
    }

    private boolean shouldFilterMessagePublicChatMessage(String playerName) {
        //Should the message be filtered, only for onChatMessage (ScriptCallback) and the public set, based on the sender's name.
        //For the rest, see shouldFilterMessage
        final Set<ChatTabFilter> chatTabFilters = publicChatFilters;
        final Set<ChatTabFilter> OHChatTabFilters = publicOHChatFilters;
        if (chatTabFilters.isEmpty()) { //Custom is not meant to be on in this case anyway
            //No null check because publicChatFilters and publicOHChatFilters cannot be null (final and declared at the top)
            return false;
        }

        playerName = Text.standardize(playerName); //Very likely works considering other methods work with a standardized name. Can't test this though since my name doesn't have e.g. a space.
        //Could probs do something like creating a map, then looping through that but meh
        if (Strings.isNullOrEmpty(playerName)
                || playerName.equals(Text.standardize(client.getLocalPlayer().getName())) //If it's your own message, don't filter
                || (chatTabFilters.contains(ChatTabFilter.FRIENDS) && !OHChatTabFilters.contains(ChatTabFilter.FRIENDS) && client.isFriended(playerName, false))
                || (chatTabFilters.contains(ChatTabFilter.FC) && !OHChatTabFilters.contains(ChatTabFilter.FC) && channelStandardizedUsernames.contains(playerName))
                || (chatTabFilters.contains(ChatTabFilter.CC) && !OHChatTabFilters.contains(ChatTabFilter.CC) && clanTotalStandardizedUsernames.contains(playerName))  //Can just use this Set instead of getClanSet(chatTabFilters) since this will always be the case for public chat
                || (chatTabFilters.contains(ChatTabFilter.GUEST_CC) && !OHChatTabFilters.contains(ChatTabFilter.GUEST_CC) && guestClanTotalStandardizedUsernames.contains(playerName))  //Can just use this Set instead of getGuestClanSet(chatTabFilters) since this will always be the case for public chat
                || (chatTabFilters.contains(ChatTabFilter.PARTY) && !OHChatTabFilters.contains(ChatTabFilter.PARTY) && runelitePartyStandardizedUsernames.contains(playerName))
                || (chatTabFilters.contains(ChatTabFilter.RAID) && !OHChatTabFilters.contains(ChatTabFilter.RAID) && raidPartyStandardizedUsernames.contains(playerName))) {
            return false;
        }

        //Get appropriate whitelist and if enabled, check if this whitelist contains the playername
        final Set<String> whitelist = getWhitelist(chatTabFilters); //Don't put this inside the if statement. You use it below.
        if (chatTabFilters.contains(ChatTabFilter.WHITELIST)
                && !OHChatTabFilters.contains(ChatTabFilter.WHITELIST)
                && whitelist != null
                && whitelist.contains(playerName)) {
            return false;
        }

        //Public = everyone that did not fit in the earlier groups: not friend, not FC/CC/Guest CC/Raid party/RL party member and not on the appropriate whitelist
        //Thus, public = the randoms
        //It's not the local player, so don't have to check for that.
        //If statement can be simplified, but specifically opted not to do this to increase readability.
        //noinspection RedundantIfStatement
        if (chatTabFilters.contains(ChatTabFilter.PUBLIC) && !OHChatTabFilters.contains(ChatTabFilter.PUBLIC) //Because if only overhead mode is active, the message should be filtered
                //Check if it is indeed a random:
                && !client.isFriended(playerName, false)
                && !channelStandardizedUsernames.contains(playerName)
                && !clanTotalStandardizedUsernames.contains(playerName) //Can just use this instead of getClanSet(chatTabFilters) since this will always be the case for public chat
                && !guestClanTotalStandardizedUsernames.contains(playerName) //Can just use this instead of getGuestClanSet(chatTabFilters) since this will always be the case for public chat
                && !runelitePartyStandardizedUsernames.contains(playerName)
                && !raidPartyStandardizedUsernames.contains(playerName)
                && (whitelist != null && !whitelist.contains(playerName))) {
            return false;
        }
        return true;
    }

    @Nullable
    private Set<String> getWhitelist(Set<ChatTabFilter> chatTabFilters) {
        //Translate the ChatTabFilters to the whitelist.
        //Switch statement is not compatible with this type, so if statements it is.
        //Alternatively probably getChatTab enum element -> return set you put in the enum
        if (chatTabFilters != null) {
            if (chatTabFilters == publicChatFilters) {
                return publicWhitelist;
            }
            if (chatTabFilters == privateChatFilters) {
                return privateWhitelist;
            }
            if (chatTabFilters == channelChatFilters) {
                return channelWhitelist;
            }
            if (chatTabFilters == clanChatFilters) {
                return clanWhitelist;
            }
            if (chatTabFilters == tradeChatFilters) {
                return tradeWhitelist;
            }
        }
        return null;
    }

    private boolean shouldFilterMessage(Set<ChatTabFilter> chatTabFilters, String playerName) {
        //Should the message be filtered, based on ChatTabFilters and the sender's name.
        //For public onChatMessage (ScriptCallback), check shouldFilterMessagePublicChatMessage!
        if (chatTabFilters == null || chatTabFilters.isEmpty()) { //Custom is not meant to be on in this case anyway, or the type does not correspond with a ChatMessageType we know.
            return false;
        }

        playerName = Text.standardize(playerName); //Very likely works considering other methods work with a standardized name. Can't test this though since my name doesn't have e.g. a space.
        //Could probs do something like creating a map, then looping through that but meh
        if (Strings.isNullOrEmpty(playerName)
                || playerName.equals(Text.standardize(client.getLocalPlayer().getName())) //Could probs do something like creating a map, then looping through that but meh
                || (chatTabFilters.contains(ChatTabFilter.FRIENDS) && client.isFriended(playerName, false))
                || (chatTabFilters.contains(ChatTabFilter.FC) && channelStandardizedUsernames.contains(playerName))
                || (chatTabFilters.contains(ChatTabFilter.CC) && getClanSet(chatTabFilters).contains(playerName))
                || (chatTabFilters.contains(ChatTabFilter.GUEST_CC) && getGuestClanSet(chatTabFilters).contains(playerName))
                || (chatTabFilters.contains(ChatTabFilter.PARTY) && runelitePartyStandardizedUsernames.contains(playerName))
                || (chatTabFilters.contains(ChatTabFilter.RAID) && raidPartyStandardizedUsernames.contains(playerName))) {
            return false;
        }

        //Get appropriate whitelist and if enabled, check if this whitelist contains the playername
        final Set<String> whitelist = getWhitelist(chatTabFilters); //Don't put inside if statement; you use this below.
        if (chatTabFilters.contains(ChatTabFilter.WHITELIST) && whitelist != null && whitelist.contains(playerName)) {
            return false;
        }

        //Public = everyone that did not fit in the earlier groups: not friend, not FC/CC/Guest CC/Raid party/RL party member and not on the appropriate whitelist
        //Thus, public = the randoms
        //It's not the local player, so don't have to check for that.
        //noinspection RedundantIfStatement
        if (chatTabFilters.contains(ChatTabFilter.PUBLIC) //If statement can be simplified, but specifically opted not to do this to increase readability.
                && !client.isFriended(playerName, false)
                && !channelStandardizedUsernames.contains(playerName)
                && !getClanSet(chatTabFilters).contains(playerName)
                && !getGuestClanSet(chatTabFilters).contains(playerName)
                && !runelitePartyStandardizedUsernames.contains(playerName)
                && !raidPartyStandardizedUsernames.contains(playerName)
                && (whitelist != null && !whitelist.contains(playerName))) {
            return false;
        }
        return true;
    }

        //Alternatively do something like this for public ChatTabFilters, but I don't really like this solution.
		/*
		if (chatTabFilters.contains(ChatTabFilter.PUBLIC)) {
			if (client.isFriended(playerName, false) || (whitelist != null && whitelist.contains(playerName))) {
				return false;
			}
			for (Set<String> set : standardizedUsernamesSet) {
				if (set.contains(playerName)) {
					return false;
				}
			}
		}
		*/

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

    //Get the appropriate clanUsernamesSet based on the ChatTabFilters and if guest trades are shown in config option or not (default: false)
    private Set<String> getClanSet(Set<ChatTabFilter> chatTabFilters) {
        if (chatTabFilters == tradeChatFilters && !showGuestTrades) {
            return clanMembersStandardizedUsernames;
        }
        return clanTotalStandardizedUsernames;
    }

    //Get the appropriate guestClanUsernamesSet based on the ChatTabFilter and if guest trades are shown in config option or not (default: false)
    private Set<String> getGuestClanSet(Set<ChatTabFilter> chatTabFilters) {
        if (chatTabFilters == tradeChatFilters && !showGuestTrades) {
            return guestClanMembersStandardizedUsernames;
        }
        return guestClanTotalStandardizedUsernames;
    }

    //Top part of the ToB board names are dynamic children of 50.27, e.g. D50.27[0], D50.27[1], D50.27[2] etc.
    //[0], [11], [22] etc is the whole line; these are useless for info but always type 3.
    //[1], [12], [23] etc are the usernames. These are type 4. (The levels etc. are also type 4; thus it's type 3 followed by a lot of type 4s of which the first is a username)
    //Bottom part of the ToB board names are dynamic children of 50.42, e.g. D50.42[0], D50.42[1], [2], [4], [6], [8], [10], [12], [14], [16], [18], [20], [21], [22], [24]
    //[0] and [20] are the whole lines again, type 3.
    //[1] and [21] are the usernames, type 4.
    private void processToBBoard() {
        //Process the ToB board
        final Widget topHalfToBBoardWidget = client.getWidget(TOB_BOARD_ID, TOP_HALF_TOB_BOARD_CHILDID);
        final Widget bottomHalfToBBoardWidget = client.getWidget(TOB_BOARD_ID, BOTTOM_HALF_TOB_BOARD_CHILDID);
        processBoard(topHalfToBBoardWidget, bottomHalfToBBoardWidget);
    }

    private void processBoard(Widget topOrMembersPart, Widget bottomOrApplicantsPart) {
        //Since processing the ToB and ToA boards works the same, this method works for both.
        //Please refer to processToBBoard and processToABoard for more info.
        final Set<String> raidPartyStandardizedUsernamesTemp = new HashSet<>();
        if (topOrMembersPart != null && topOrMembersPart.getDynamicChildren() != null) {
            for (int i = 0; i < topOrMembersPart.getDynamicChildren().length; i++) {
                //Get child that has type 3 => next one has to be the username
                final Widget child = topOrMembersPart.getChild(i);
                if (child != null && child.getType() == 3) {
                    //Index of the one that has name is type 3 index + 1
                    final Widget nameWidget = topOrMembersPart.getChild(i + 1);
                    if (nameWidget != null && nameWidget.getType() == 4) {
                        //If right type (4), get the text and standardize it
                        final String standardizedRaidUsername = Text.standardize(nameWidget.getText()); //Also removes the leading and trailing spaces from -
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
                final Widget child = bottomOrApplicantsPart.getChild(i);
                if (child != null && child.getType() == 3) {
                    //Index of the one that has name is type 3 index + 1
                    final Widget nameWidget = bottomOrApplicantsPart.getChild(i + 1);
                    if (nameWidget != null && nameWidget.getType() == 4) {
                        //If right type (4), get the text and standardize it, then add it to the temp HashSet
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

    //No party text = -<br>-<br>-<br>-<br>-
    //Party text = Username<br>Username2<br>-<br>-<br>-
    private void processToBPartyInterface() {
        //Processed the party interface in the ToB lobby
        final Widget tobPartyInterfaceNamesWidget = client.getWidget(InterfaceID.TOB, TOB_PARTY_INTERFACE_NAMES_CHILDID); //S28.12
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

        //Set previousRaidPartyInterfaceText before modifying raidPartyInterfaceText
        previousRaidPartyInterfaceText = raidPartyInterfaceText;
        //Process raidPartyInterfaceText and add to HashSet if needed
        raidPartyInterfaceText = raidPartyInterfaceText + "<br>"; //Append <br> so indexOf and substring works for every item
        for (int i = 0; i < 8; i++) {
            final int idx = raidPartyInterfaceText.indexOf("<br>");
            if (idx != -1) {
                final String standardizedUsername = Text.standardize(raidPartyInterfaceText.substring(0, idx));
                //Prevent empty strings or strings equalling "-" being added to the HashSet.
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
        //Add all standardized names of TOB and TOA raiders to a HashSet.
        //Useful for when starting the plugin while inside TOB/TOA.
        for (int i = 0; i < 5; i++) {
            addInRaidUsernamesVarClientStr(TOB_IN_RAID_VARCSTR_PLAYER1_INDEX + i);
        }
        for (int i = 0; i < 8; i++) {
            addInRaidUsernamesVarClientStr(TOA_IN_RAID_VARCSTR_PLAYER1_INDEX + i);
        }
    }

    private void addInRaidUsernamesVarClientStr(int varCStrIndex) {
        //Add the standardized names of TOB and TOA raiders to a HashSet.
        //Hashset can't contain dupes and the add method already checks if it contains it or not.
        if (!(varCStrIndex >= TOB_IN_RAID_VARCSTR_PLAYER1_INDEX && varCStrIndex <= TOB_IN_RAID_VARCSTR_PLAYER5_INDEX)
                && !(varCStrIndex >= TOA_IN_RAID_VARCSTR_PLAYER1_INDEX && varCStrIndex <= TOA_IN_RAID_VARCSTR_PLAYER8_INDEX)) {
            //If not tob or toa VarCStrIndex, return
            return;
        }

        final String varCStrValueStandardized = Text.standardize(client.getVarcStrValue(varCStrIndex));
        //isNullOrEmpty check because they get refreshed in probably every room and can potentially add empty strings to the HashSet.
        if (!Strings.isNullOrEmpty(varCStrValueStandardized) && raidPartyStandardizedUsernames.add(varCStrValueStandardized)) {
            shouldRefreshChat = true;
        }
    }

    //Member tab of the ToA board names are dynamic children of S774.32, e.g. D774.32[0], D774.32[1], D774.32[2] etc.
    //[0], [13], [26], [39] etc is the whole line; these are useless for info but always type 3.
    //[1], [14], [27], [40] etc are the usernames. These are type 4. (The levels etc. are also type 4; thus it's type 3 followed by a lot of type 4s of which the first is a username)
    //Applicants tab of the ToA board names are dynamic children of S774.48, e.g. D774.48[0], D774.48[1], [2], [4], [6], [8], [10], [12], [14], [16], [18], [20], [21], [22], [24] etc
    //[0] and [20] are the whole lines again, type 3.
    //[1] and [21] are the usernames, type 4.
    private void processToABoard() {
        //When you are on a different tab than Members (Applicants, Invocations, Summary), the widget is hidden but not null! Thus, even while on a different tab, you can get the current members. Those are needed to determine if it is your party.
        final Widget membersToABoardWidget = client.getWidget(TOA_BOARD_ID, MEMBERS_TOA_BOARD_CHILDID);
        final Widget applicantsToABoardWidget = client.getWidget(TOA_BOARD_ID, APPLICANTS_TOA_BOARD_CHILDID);
        processBoard(membersToABoardWidget, applicantsToABoardWidget);
    }

    //No party text = -<br>-<br>-<br>-<br>-<br>-<br>-<br>-
    //Party text = Username<br>Username2<br>-<br>-<br>-<br>-<br>-<br>-
    private void processToAPartyInterface() {
        //Processed the party interface in the ToA lobby
        final Widget toaPartyInterfaceNamesWidget = client.getWidget(InterfaceID.TOA_PARTY, TOA_PARTY_INTERFACE_NAMES_CHILDID); //S773.5
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
    private void addCoXBankPlayers() {
        //Add people to the appropriate set in the cox bank area if they are in the FC
        //Procs every gametick while in the cox bank regionID. Check varp so it only procs in the bank area.
        //Cox bank people can technically not be in the FC yet when spawning or run up the CoX stairs with you so execute every gametick instead of onplayerspawned
        if (client.getVarpValue(VarPlayer.IN_RAID_PARTY) == -1) {
            return;
        }

        final FriendsChatManager friendsChatManager = client.getFriendsChatManager();
        if (friendsChatManager == null) {
            return;
        }

        //Loop through players and add them to HashSet if they are in the FC
        for (Player player : client.getPlayers()) {
            final String standardizedUsername = Text.standardize(player.getName());
            if (friendsChatManager.findByName(standardizedUsername) != null
                    && !Strings.isNullOrEmpty(standardizedUsername)
                    && raidPartyStandardizedUsernames.add(standardizedUsername)) {
                shouldRefreshChat = true;
            }
        }
    }

    private void setAddPartyMembersFlag() {
        //partyService.getMembers() is empty when immediately running this after joining a party. Set a flag to retry 5 gameticks or till the list is not empty.
        getRLPartyMembersFlag = 5;
        addPartyMembers();
    }

    private void addPartyMembers() {
        //Add all the RL party members to the appropriate set
        //Opted to use this so party members would remain until hopping.
        //Alternatively just use partyService.isInParty() && partyService.getMemberByDisplayName(player.getName()) != null in the shouldFilter code if you only want it to be when they are in the party.
        if (!partyService.isInParty()) {
            //Return early if not in party
            return;
        }

        final List<PartyMember> partyMembers = partyService.getMembers();
        if (partyMembers == null || partyMembers.isEmpty()) {
            return;
        }

        //The list is not empty anymore (can be empty immediately after joining a party) ->
        // set the flag to 0 and add the partymembers to the correct HashSet
        getRLPartyMembersFlag = 0;
        for (PartyMember partyMember : partyMembers) {
            final String standardizedUsername = Text.standardize(partyMember.getDisplayName());
            if (!Strings.isNullOrEmpty(standardizedUsername) && runelitePartyStandardizedUsernames.add(standardizedUsername)) {
                shouldRefreshChat = true;
            }
        }
        System.out.println(runelitePartyStandardizedUsernames); //todo: remove
    }

    private void setAddUserJoinedPartyMembersFlag() {
        //getDisplayName is sometimes not yet available onUserJoined, while the memberId is. So set a flag to retry.
        getRLPartyUserJoinedMembersFlag = 5;
        addUserJoinedPartyMembers();
    }

    private void addUserJoinedPartyMembers() {
        //If username could not be determined onUserJoin, the memberIds were added to a HashSet.
        //Go through the HashSet, add the standardized usernames to the HashSet.
        boolean allMembersProcessed = true;
        for (long memberId : partyMemberIds) {
            final String standardizedUsername = Text.standardize(partyService.getMemberById(memberId).getDisplayName());
            if (!Strings.isNullOrEmpty(standardizedUsername)) { // Not combined with if statement below so if else can be used
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
        if (getRLPartyUserJoinedMembersFlag == 0) { //Clear the HashSet with party member ids when flag = 0. setAddUserJoinedPartyMembersFlag sets flag back to 5 in case a user joins while the flag is going down.
            partyMemberIds.clear();
        }
    }

    private boolean shouldShowShiftMenuSetting(ShiftMenuSetting shiftMenuSetting) {
        //Should the ChangeChatSetsMenu be shown based on the config setting?
        switch (shiftMenuSetting) {
            case ALWAYS:
                return true;
            case HOLD_SHIFT:
                return shiftModifier();
        }
        //These menus are required for core functionality, so return true if unsure
        return true;
    }

    private boolean shouldShowShiftMenuSetting(ShiftMenuSettingOptional shiftMenuSettingOptional) {
        //Should some menus that are not required for core functionality (optional) be shown based on the config setting?
        switch (shiftMenuSettingOptional) {
            case ALWAYS:
                return true;
            case HOLD_SHIFT:
                return shiftModifier();
            case DISABLED:
                return false;
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