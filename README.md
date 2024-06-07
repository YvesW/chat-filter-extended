# Example
An example greeter plugin

PLUGIN IS NOT FINISHED YET AND README HAS NOT BEEN MADE YET

GIM chat support has not been tested since I don't have a GIM. It should likely behave like the CC filter.

add info somewhere on OH works: this is already added to the config item, but since the user does not need to interact with it, also add it to the readme  
excerpt from config: "Allowed chat senders when the custom filter is active (overhead text). Needs to also be active in the set above.<br>"+  
"Thus enabling 'Friends' in the set above and 'Friends (OH)' in this set does not show your friends' messages in the chatbox, but it does show it above their head.<br>"  
aka a filter needs to be active and then its OH filter needs to be active to show the messages as overhead

FC/CC/Guest CC members remembered till logout or smth (well till leaving channel so also hopping/logout for FC and guest CC, but only logout for CC?)  
Raid remembered till x y z for reasons such as splitting, also takes into account applying at tob/toa, at cox it checks x and y such as zone or like fc is then the same  
Maybe add some of this stuff to an advanced config?  
RL Party: cleared on logout & hopping probs.  
Tob: when applying to a team/when someone applies to your team, when in lobby, when in raid => reset when hopping/logging out? also when e.g. entering toa lobby? also add reset button  
Toa: toa board (including applicants of your party/when you apply to someone's party), toa lobby party interface, toa in raid => reset when hopping or logging out, also when entering other raid lobby? also add reset button  
Cox: in een party en in cox bank zone, in cox lobby, in cox raid. resets on hop and logout, maybe also when joining other raid area? add reset option  
FC: //Remove FC usernames when leaving the FC and when the advanced config option is enabled; also procs when hopping/logging out
Clan: including guests  
Guest clan: including guests  
Maybe gim idk: -> just added to clan hashset iirc  
Friends:  
Public?: everything that is not part of the above categories probs  
whitelist??  
probably ctrl+f "standardizedusernames.clear()" and "clearRaidPartyHashset()"  

Also everything 2d for public (overhead)

Shift-menu thingies exist, cave when updating the set with sidepanel open, the config panel might not look updated but this is a runelite limitation. It actually does update.  
Flash thingy prevention/proper handling exists unlike default chat filter  
Notifications thing prevention exists unlike default chat filter  

Clears when it clears because it's jarring if a lot of messages suddenly disappear when you quickly hop worlds, or briefly leave your cc/fc (default advanced settings have been chosen with this in mind)

Add to readme that it does not filter your own chat so you don't have those weird situations with e.g. public chat set to private that does not display your own messages  

Thanks to Annotated for helping me collect Vars/ScriptIds and for helping me extensively test the plugin!

