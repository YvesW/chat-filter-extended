package com.ywcode.chatfilterextended;

import com.google.gson.JsonArray;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

/*
Normal Gson output = {"11058":{"puB":false,"puSOH":["RAID","PARTY"],"puS":["FRIENDS","FC","CC","GUEST_CC","RAID","PARTY","WHITELIST"],"prB":false,"prS":[],"chB":false,"chS":[],"clB":false,"clS":[],"trB":false,"trS":[]},"11310":{"puB":true,"puSOH":[],"puS":[],"prB":false,"prS":[],"chB":false,"chS":[],"clB":true,"clS":[],"trB":false,"trS":[]}}
Potential option close in amount of chars to previous implementation:
{11058:{puOHS:[ra,pa],puS:[fr,fc,cc,gu,ra,pa,wh]},11310:{B:[puB,clB]}} -> the FilteredRegion would be {puOHS:[ra,pa],puS:[fr,fc,cc,gu,ra,pa,wh]} and {B:[puB,clB]}
The goal is to get a relatively small JSON/JSON-like string. This does make the output compliant with JSON as specified by RFC 4627. It's set to lenient and will be quite lenient...
Thus, the JSON will be semi-compact, but also very brittle (and not even JSON anymore with e.g. most quotation marks removed).

Rules:
1. Field names are abbreviated to their first 2 lower case letters, followed by B for booleans or S for sets. The OH set is puOHS.
2. Remove a field with a default value
3. Use the abbreviations for the values of the sets
4. For booleans, only include the abbreviated name if they're true
5. Try to remove all quotation marks to save quite a bit of space -> not JSON anymore afterwards though -> done in ChatFilterExtendedPlugin.getFilteredRegionsJson()

Result of previous code (that did not use Gson):
11058:pu;fr/fc/cc/gu/ra/pa/wh/raoh/paoh,11310:pu;cu,11310:cl;cu

I could still do really stupid things like:
- Since puS will have to contain chats for them to be useful in puOHS (not active otherwise), you could replace
  OH ChatTabFilters in puS with e.g. frO. This would have to be 1 added letter max, otherwise the current approach is
  probably more efficient for longer OH sets. This would be somewhat annoying to write and read though. It'd probs require
  for looping and replacing stuff while writing, and for looping to add to extra set -> replacing -> looping for non-OH set.
  Also, the stupid second idea would save way more space.
- Replace everything with 1 letter serialized names, but this makes the "Json" even more brittle and definitely impossible
  to ever understand (which it basically already is for everyone but me).
 */
public class FilteredRegionAdapter extends TypeAdapter<FilteredRegion> {
    private static boolean setName; //If the boolean array has set the name and the array has begun
    private static final String booleanAbbreviation = "B"; //boolean abbreviation

    @Override
    public void write(final JsonWriter jsonWriter, final FilteredRegion filteredRegion) throws IOException {
        jsonWriter.beginObject(); //Begin the object {
        //Write all sets if they're not the default value
        writeSet(jsonWriter, filteredRegion.getPublicOHChatSet(), FilteredRegionField.PUBLIC_OH_CHAT_SET);
        writeSet(jsonWriter, filteredRegion.getPublicChatSet(), FilteredRegionField.PUBLIC_CHAT_SET);
        writeSet(jsonWriter, filteredRegion.getPrivateChatSet(), FilteredRegionField.PRIVATE_CHAT_SET);
        writeSet(jsonWriter, filteredRegion.getChannelChatSet(), FilteredRegionField.CHANNEL_CHAT_SET);
        writeSet(jsonWriter, filteredRegion.getClanChatSet(), FilteredRegionField.CLAN_CHAT_SET);
        writeSet(jsonWriter, filteredRegion.getTradeChatSet(), FilteredRegionField.TRADE_CHAT_SET);
        writeBooleans(jsonWriter, filteredRegion); //Write the booleans if they are not the default value
        jsonWriter.endObject(); //End the object }
    }

    @Override
    public FilteredRegion read(final JsonReader jsonReader) {
        //JsonReader for FilteredRegion
        //Way too much peeking to prevent errors from people fucking with the semi-Json String. Probably not even
        // necessary since I can just try catch the exceptions when calling fromGson (and I'll need to do that anyway
        // because I'm not writing a custom adapter for the Map), but oh well.
        final FilteredRegion filteredRegion = new FilteredRegion();

        try {
            if (jsonReader.peek() != JsonToken.BEGIN_OBJECT) {
                //Set message and return if not starting with BEGIN_OBJECT
                //Will probs result in an error in MapTypeAdapterFactory anyway, so will still cause problems but enfin.
                setFilteredRegionsMalformedString("FilteredRegion should begin with { (BEGIN_OBJECT).");
                return filteredRegion;
            }

            jsonReader.beginObject(); //beginObject, {
            while (jsonReader.hasNext()) { //PM Call while (jsonReader.hasNext()) after every beginObject and beginArray!
                if (jsonReader.peek() != JsonToken.NAME) {
                    //Set message, skipValue, and continue if it's not NAME
                    //Will probs result in an error in MapTypeAdapterFactory anyway, so will still cause problems but enfin.
                    setFilteredRegionsMalformedString("FilteredRegion should contain a NAME.");
                    jsonReader.skipValue(); //Skip value if incorrect (otherwise infinite loop if just continue, since the token is not consumed)
                    continue;
                }
                final String nextName = jsonReader.nextName(); //Get the name. This consumes the token (idem with nextString etc.), unlike peek

                //Both the booleans and set values start with an array, so already peek and begin the array
                if (jsonReader.peek() != JsonToken.BEGIN_ARRAY) {
                    //Set message, skipValue, and continue if it's not BEGIN_ARRAY
                    //Will probs result in an error in MapTypeAdapterFactory anyway, so will still cause problems but enfin.
                    setFilteredRegionsMalformedString("FilteredRegion values should start with [ (BEGIN_ARRAY).");
                    jsonReader.skipValue();
                    continue;
                }

                //Declare chatTabFilters here; to be used if it's a set. This way it's not being continually reset in the while loop of the array below
                final Set<ChatTabFilter> chatTabFilters = EnumSet.noneOf(ChatTabFilter.class);

                jsonReader.beginArray(); //Begin array for either booleans or set values
                while (jsonReader.hasNext()) {
                    //Both the booleans and set values contain strings, so already peek
                    if (jsonReader.peek() != JsonToken.STRING) {
                        //Set message, skipValue, and continue if it's not STRING
                        setFilteredRegionsMalformedString("FilteredRegion values should be of type STRING.");
                        jsonReader.skipValue();
                        //Worked perfectly with {puS:[123456,fc,cc,gu,ra,pa,wh]}
                        continue;
                    }

                    //If it's the booleans array, get the nextString and convert to FilteredRegionField. If it's a set, convert the name to FilteredRegionField.
                    final String booleanNextStringSetNextName = nextName.equalsIgnoreCase(booleanAbbreviation) ? jsonReader.nextString() : nextName;
                    final FilteredRegionField filteredRegionField = FilteredRegionField.getEnumElement(booleanNextStringSetNextName);

                    if (filteredRegionField == null) {
                        //Have to null check because we can't do it in e.g. a switch like in Java >= 18
                        //Either the booleans value is not the serialized name, or the set name is not the serialized name.
                        setFilteredRegionsMalformedString("FilteredRegion booleans value / set name is not the serialized name.");
                        jsonReader.skipValue();
                        //Originally no skipValue because I thought the String had already been consumed, either via nextName way before, or nextString above (we're not peeking)
                        //11058:{uS:[fr,fc,cc,gu,ra,pa,wh]} resulted in an infinite loop though, so skipValue was clearly needed (this procced 7 times in total afterwards)
                        continue;
                    }

                    if (nextName.equalsIgnoreCase(booleanAbbreviation)) {
                        //PM Since both boolean and set values should start with BEGIN_ARRAY, while loop, and then contain STRINGs,
                        // and also check for a filteredRegionField (one based on nextString, one based on nextName though!) I did this before if name.equals statement
                        // If in the future you add values that are not arrays, add BEGIN_ARRAY, while loop, peek STRING, get filteredRegionField code here!

                        switch (filteredRegionField) {
                            case PUBLIC_CHAT_CUSTOM_ONLY:
                                filteredRegion.setPublicChatCustomOnly(true);
                                break;
                            case PRIVATE_CHAT_CUSTOM_ONLY:
                                filteredRegion.setPrivateChatCustomOnly(true);
                                break;
                            case CHANNEL_CHAT_CUSTOM_ONLY:
                                filteredRegion.setChannelChatCustomOnly(true);
                                break;
                            case CLAN_CHAT_CUSTOM_ONLY:
                                filteredRegion.setClanChatCustomOnly(true);
                                break;
                            case TRADE_CHAT_CUSTOM_ONLY:
                                filteredRegion.setTradeChatCustomOnly(true);
                                break;
                            default:
                                //Not null, so the booleans array value is a FilteredRegionField.serializedName but not a boolean, so e.g. a set like PUBLIC_CHAT_SET
                                setFilteredRegionsMalformedString("FilteredRegion booleans array value has to be of boolean serialized name type, not set.");
                                //No skipValue because String has already been consumed (we're not peeking)
                                //Worked perfectly with {B:[puS,clB]}
                                continue; //Could be removed since it's the end of the loop, but maybe I'll add some code below at some point
                        }
                        //PM Since the sets also use arrays, END_ARRAY is being performed at the end (after else statement closes)
                        //If you add values that are not arrays, add END_ARRAY code here!

                    } else { //nextName is not booleanAbbreviation
                        //PM Since both boolean and set values should start with BEGIN_ARRAY, while loop, and then contain STRINGs,
                        // and also check for a filteredRegionField (one based on nextString, one based on nextName though!) I did this before if name.equals statement
                        // If in the future you add values that are not arrays, add BEGIN_ARRAY, while loop, peek STRING, get filteredRegionField code here!

                        final ChatTabFilter chatTabFilter = ChatTabFilter.getEnumElement(jsonReader.nextString());
                        if (chatTabFilter == null) {
                            //Not a valid chatTabFilter, so continue
                            setFilteredRegionsMalformedString("FilteredRegion set array value has to be of ChatTabFilter serialized name type.");
                            //No skipValue because String has already been consumed (we're not peeking).
                            //{puS:[f,f,cc,gu,ra,pa,wh]} worked perfectly
                            continue;
                        }
                        chatTabFilters.add(chatTabFilter); //Add chatTabFilter to EnumSet which will be used with setter

                        //Now set the proper set based on the filteredRegionField
                        switch (filteredRegionField) {
                            case PUBLIC_OH_CHAT_SET:
                                filteredRegion.setPublicOHChatSet(chatTabFilters);
                                break;
                            case PUBLIC_CHAT_SET:
                                filteredRegion.setPublicChatSet(chatTabFilters);
                                break;
                            case PRIVATE_CHAT_SET:
                                filteredRegion.setPrivateChatSet(chatTabFilters);
                                break;
                            case CHANNEL_CHAT_SET:
                                filteredRegion.setChannelChatSet(chatTabFilters);
                                break;
                            case CLAN_CHAT_SET:
                                filteredRegion.setClanChatSet(chatTabFilters);
                                break;
                            case TRADE_CHAT_SET:
                                filteredRegion.setTradeChatSet(chatTabFilters);
                                break;
                            default:
                                //Not null, so it's a FilteredRegionField.serializedName but not a set so e.g. a boolean like PUBLIC_CHAT_CUSTOM_ONLY
                                setFilteredRegionsMalformedString("FilteredRegion set array value has to be of ChatTabFilter serialized name type.");
                                //No skipValue because String has already been consumed (we're not peeking)
                                //Worked perfectly with {clB:[fr,fc,cc,gu,ra,pa,wh]}
                                continue; //Could be removed since it's the end of the loop, but maybe I'll add some code below at some point
                        }
                    }
                }

                //End the array for either the booleans value array or a set value array
                if (jsonReader.peek() != JsonToken.END_ARRAY) {
                    //Set message, skipValue, and continue if it's not END_ARRAY
                    setFilteredRegionsMalformedString("FilteredRegion values should end with ] (END_ARRAY).");
                    jsonReader.skipValue();
                    //Will probs result in an error in MapTypeAdapterFactory anyway, so will still cause problems but enfin.
                    continue;
                }
                jsonReader.endArray(); //End array (either booleans value array, or set value array)
            }

            if (jsonReader.peek() != JsonToken.END_OBJECT) {
                //Set message and return if not ending with END_OBJECT
                setFilteredRegionsMalformedString("FilteredRegion should end with } (END_OBJECT).");
                //Will probs result in an error in MapTypeAdapterFactory anyway, so will still cause problems but enfin.
                return filteredRegion;
            }
            jsonReader.endObject(); //End the object for the FilteredRegion

        } catch (IOException ioException) {
            //Set message and return filteredRegion in case of an IOException
            setFilteredRegionsMalformedString("IOException: " + ioException.getMessage());
            return filteredRegion;
        }
        return filteredRegion;
    }

    private void setFilteredRegionsMalformedString(String message) {
        if (ChatFilterExtendedPlugin.getFilteredRegionsMalformedString() != null) {
            //String is not null, don't overwrite. Otherwise, the message of a problem caused because of an earlier problem might be outputted.
            return;
        }
        ChatFilterExtendedPlugin.setFilteredRegionsMalformedString(message);
    }

    private void writeSet(JsonWriter jsonWriter, Set<ChatTabFilter> chatTabFilters, FilteredRegionField filteredRegionField) throws IOException {
        //Write the serialized name of the set if this is not empty, then the abbreviated values
        if (chatTabFilters.isEmpty()) {
            //ChatTabFilters is empty (default value), return
            return;
        }

        jsonWriter.name(filteredRegionField.getSerializedName()); //Write the serialized name

        //Translate the chatTabFilters (or OHChatTabFilters) to a JsonArray with abbreviated strings
        final JsonArray jsonArray = new JsonArray();
        for (ChatTabFilter chatTabFilter : chatTabFilters) {
            jsonArray.add(chatTabFilter.getFilteredRegionAbbreviation());
        }

        //JsonArray.toString(), then output as jsonValue, which writes the value directly to the writer without quoting or escaping.
        // Could technically .replace("\"", "") here as well, but this is done in getFilteredRegionsJson() anyway
        jsonWriter.jsonValue(jsonArray.toString());
    }

    private void writeBooleans(JsonWriter jsonWriter, FilteredRegion filteredRegion) throws IOException {
        //Start a boolean array
        writeBoolean(jsonWriter, filteredRegion.isPublicChatCustomOnly(), FilteredRegionField.PUBLIC_CHAT_CUSTOM_ONLY);
        writeBoolean(jsonWriter, filteredRegion.isPrivateChatCustomOnly(), FilteredRegionField.PRIVATE_CHAT_CUSTOM_ONLY);
        writeBoolean(jsonWriter, filteredRegion.isChannelChatCustomOnly(), FilteredRegionField.CHANNEL_CHAT_CUSTOM_ONLY);
        writeBoolean(jsonWriter, filteredRegion.isClanChatCustomOnly(), FilteredRegionField.CLAN_CHAT_CUSTOM_ONLY);
        writeBoolean(jsonWriter, filteredRegion.isTradeChatCustomOnly(), FilteredRegionField.TRADE_CHAT_CUSTOM_ONLY);

        if (setName) {
            //End the array if it has begun, aka when setName == true
            jsonWriter.endArray();
            setName = false; //Yes, this is needed to set the state back to false for the next FilteredRegion. I should use a better way to determine this.
        }
    }

    private void writeBoolean(JsonWriter jsonWriter, boolean bool, FilteredRegionField filteredRegionField) throws IOException {
        if (!bool) {
            //return if boolean is not false (default value)
            return;
        }

        //Set the name and begin the array if it has not been set yet
        if (!setName) {
            setName = true;
            jsonWriter.name(booleanAbbreviation).beginArray();
        }

        //Write the serialized name as value to the array if the boolean is true
        jsonWriter.jsonValue(filteredRegionField.getSerializedName());
    }
}