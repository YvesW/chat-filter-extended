package com.ywcode.chatfilterextended;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.*;
import net.runelite.client.config.*;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.*;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.util.*;

@Slf4j
@PluginDescriptor(
		name = "Chat Filter Extended",
		description = "Adds the ability to filter all chat messages not coming from friends/clanmates/FC members/Guest CC members/raid members.",
		tags = {"public,chat,cc,fc,clanchat,clan,filter,friendschat,friends,raids"}
)

public class ChatFilterExtendedPlugin extends Plugin {

	// ------------- Wall of config vars -------------
	private static boolean showFriendsMessages;
	private static boolean showCCMessages;
	private static boolean showFCMessages;
	private static boolean showGuestCCMessages;
	private static boolean showRaidPartyMessages;
	private static boolean forcePrivateOn;
	private static Set<ChatsToFilter> chatsToFilter = new HashSet<ChatsToFilter>();
	// ------------- End of wall of config vars -------------

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
		updateConfig();
		//todo: add icon, license, readme
	}

	@Override
	public void shutDown() {
		//TODO: check if something needs to be added to startup or shutdown
		//todo: unregister shit that adds extra option to public chat (probs al automatisch tho want menu wordt gesloten), set widget text back to normal if activated and disable filter/restore normal text
		//todo: potentially do some of this shit or stop filter when swapping profiles? check how other filter plugins do it
		//todo: potentially also filter trade to this?
		//todo: find a way to disable filtering mode when disabling all the show friends/show cc etc options are disabled + to change the filter mode when config is changed + change text back in that case
		//todo: check different interface styles via interface styles plugin (and if changing the style fucks it up)
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged) {
		if (configChanged.getGroup().equals("publicccfilter")) {
			updateConfig();
		}
	}

	private void updateConfig() {
		showFriendsMessages = config.showFriendsMessages();
		showCCMessages = config.showCCMessages();
		showFCMessages = config.showFCMessages();
		showGuestCCMessages = config.showGuestCCMessages();
		showRaidPartyMessages = config.showRaidPartyMessages();
		chatsToFilter = config.chatsToFilter();
		forcePrivateOn = config.forcePrivateOn();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)	{
			//todo: potentially only do shit while logged in/or when red click to play screen is gone? check how other plugins add element and filter chat
		}
	}

	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged clanChannelChanged) {
	}

	@Subscribe
	public void onClanMemberJoined(ClanMemberJoined clanMemberJoined) {
	}

	@Subscribe
	public void onClanMemberLeft(ClanMemberLeft clanMemberLeft) {
	}

	@Subscribe
	public void onFriendsChatChanged(FriendsChatChanged friendsChatChanged) {
	}

	@Subscribe
	public void onFriendsChatMemberJoined(FriendsChatMemberJoined friendsChatMemberJoined) {
	}

	@Subscribe
	public void onFriendsChatMemberLeft(FriendsChatMemberLeft friendsChatMemberLeft) {
	}

	@Subscribe
	public void onRemovedFriend(RemovedFriend removedFriend) {
		//TODO: way to detect FL, add friends to it when adding, remove friends from it when removing, clearing on logout probs
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted commandExecuted) {
		if (commandExecuted.getCommand().contains("test")) {
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
						client.setVarbit(928,0);
						client.runScript(152, Integer.parseInt(test), Integer.parseInt(test2));
						client.setVarbit(928,0);
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
			clientThread.invokeLater(() -> {
				client.setVarbit(928,0);
				client.runScript(152, 4, 0);
				client.setVarbit(928,0);
				client.setVarbit(929,0);
				client.runScript(152, 5, 0);
				client.setVarbit(929,0);
			});
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage) {
		if (chatMessage.getType() == ChatMessageType.PUBLICCHAT && chatMessage.getName() == client.getLocalPlayer().getName()) {

		}
	}

	@Subscribe(priority = -3)
	public void onMenuEntryAdded(MenuEntryAdded menuEntryAdded) {
		if (menuEntryAdded.getType() != MenuAction.CC_OP.getId()) {
			return;
		}
		//getActionParam1() seems to be getMenuEntry().getParam1() which seems to be getMenuEntry().getWidget().getId() = 10616843 (for public chat).
		//Only show MenuEntry when one of the filter options is enabled
		if (shouldFilterChatType(widgetIDtoWidgetInfo(menuEntryAdded.getActionParam1())) && menuEntryAdded.getOption().contains("Show none") &&
				(showFriendsMessages || showCCMessages || showFCMessages || showGuestCCMessages || showRaidPartyMessages)) {
			final MenuEntry ccFilterEntry = client.createMenuEntry(-1).setType(MenuAction.RUNELITE_HIGH_PRIORITY);
			ccFilterEntry.setParam1(menuEntryAdded.getActionParam1()).onClick(this::setChatFilterConfig);

			final StringBuilder optionBuilder = new StringBuilder();
			//Pull tab name from menu since Trade/Group is variable
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
			optionBuilder.deleteCharAt(optionBuilder.length() - 1);
			ccFilterEntry.setOption(optionBuilder.toString());
		}

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
		ChatboxTab tab = ChatboxTab.of(menuEntryAdded.getActionParam1());
		if (tab == null || tab.getAfter() == null || !config.clearHistory() || !menuEntryAdded.getOption().endsWith(tab.getAfter()))
		{
			return;
		}

		final MenuEntry clearEntry = client.createMenuEntry(-2)
				.setType(MenuAction.RUNELITE_HIGH_PRIORITY);
		clearEntry.setParam1(menuEntryAdded.getActionParam1());

		final StringBuilder optionBuilder = new StringBuilder();
		if (tab != ChatboxTab.ALL)
		{
			// Pull tab name from menu since Trade/Group is variable
			String option = menuEntryAdded.getOption();
			int idx = option.indexOf(':');
			if (idx != -1)
			{
				optionBuilder.append(option, 0, idx).append(":</col> ");
			}
		}

		optionBuilder.append(CLEAR_HISTORY);
		clearEntry.setOption(optionBuilder.toString()); */
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked menuOptionClicked) {
		final String menuOption = menuOptionClicked.getMenuOption();

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

		//The menu option for clear history is "<col=ffff00>Public:</col> Clear history"
		if (menuOption.contains("<col=ffff00>Public:</col> CC/FC/Friends"))	{
			//clearChatboxHistory(ChatboxTab.of(event.getParam1()));
			System.out.println("CC/FC/Friends clicked!");
		}
	}

	//TEST
	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged varClientIntChanged) {
		if (varClientIntChanged.getIndex() == 1112) {
			//System.out.println(client.getVarcIntValue(1112));
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened menuOpened) {
	}

	@Subscribe
	public void onMenuShouldLeftClick(MenuShouldLeftClick menuShouldLeftClick) {
	}

	@Subscribe
	public void onPostMenuSort(PostMenuSort postMenuSort) {
	}
	//TEST ended

	private boolean shouldFilterChatType(WidgetInfo widgetInfo) {
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

	private WidgetInfo widgetIDtoWidgetInfo (int widgetID) {
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

	private void setChatFilterConfig(MenuEntry menuEntry) {
		int menuWidgetID = menuEntry.getParam1();
		if (widgetIDtoWidgetInfo(menuWidgetID) != null) {
			switch (widgetIDtoWidgetInfo(menuWidgetID)) {
				case CHATBOX_TAB_PUBLIC:
					//configManager.setConfiguration();
					break;
			}
		}
	}
	//todo: rename, zie solidify discord LOL
	//TODO: test scripts to force public etc to on + doe dit en alleen als optie checked voor private + call on hop etc if needed
	//TODO: make code to set public etc to custom and back to normal shit when clicking on other menu entry if needed (check when hopping, login, closing/opening chat thingies, changing to resizable, after npc chatbox maybe, in/after cutscenes like myths guild, persist via configmanager probs after client reboots)
	//TODO: get cc, fc, all friends, guest cc, raids members + board/applicants, also add guests to your or guest cc, dont remove probs till leaving chat channel/rebooting client, add people when added to friendslist/joining as guest later on/added to clan later on
	//todo: make filter code probs (hangt tevens met vorige samen) + test


	@Provides
	ChatFilterExtendedConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(ChatFilterExtendedConfig.class);
	}
}
