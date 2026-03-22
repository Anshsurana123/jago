package com.example.jarvis.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandParserTest {

    private val parser = CommandParser()

    @Test
    fun testParseMultiCommand() {
        val input = "turn on flashlight and open whatsapp"
        val commands = parser.parse(input)
        assertEquals(2, commands.size)
        assertEquals(CommandType.FLASHLIGHT_ON, commands[0].type)
        assertEquals(CommandType.OPEN_WHATSAPP, commands[1].type)
    }

    @Test
    fun testNumericDisambiguationTo() {
        val input = "increase volume to 70"
        val commands = parser.parse(input)
        assertEquals(1, commands.size)
        val cmd = commands[0]
        assertEquals(CommandType.VOLUME_UP, cmd.type)
        assertEquals(70, cmd.numericValue)
        assertEquals(false, cmd.isRelative) // 'to' should force absolute
    }

    @Test
    fun testNumericDisambiguationBy() {
        val input = "increase volume by 20"
        val commands = parser.parse(input)
        assertEquals(1, commands.size)
        val cmd = commands[0]
        assertEquals(CommandType.VOLUME_UP, cmd.type)
        assertEquals(20, cmd.numericValue)
        assertEquals(true, cmd.isRelative) // 'by' should force relative
    }

    @Test
    fun testCloseAppIntent() {
        val input = "close whatsapp"
        val commands = parser.parse(input)
        assertEquals(1, commands.size)
        assertEquals(CommandType.CLOSE_APP, commands[0].type)
        assertEquals("whatsapp", commands[0].contactName)
    }

    @Test
    fun testMinimizeSynonym() {
        val input = "minimize youtube"
        val commands = parser.parse(input)
        assertEquals(1, commands.size)
        assertEquals(CommandType.CLOSE_APP, commands[0].type)
        assertEquals("youtube", commands[0].contactName)
    }

    @Test
    fun testQueryBrightness() {
        val input = "what is the current brightness level"
        val commands = parser.parse(input)
        assertEquals(1, commands.size)
        assertEquals(CommandType.QUERY_BRIGHTNESS, commands[0].type)
    }

    @Test
    fun testReminderFull() {
        val input = "remind me to study at 8 pm"
        val commands = parser.parse(input)
        assertEquals(1, commands.size)
        val cmd = commands[0]
        assertEquals(CommandType.SET_REMINDER, cmd.type)
        assertEquals("study", cmd.messageBody)
        assertEquals(false, cmd.missingTime)
        assertEquals(false, cmd.missingMessage)
        assert(cmd.triggerMillis != null)
    }

    @Test
    fun testReminderRelative() {
        val input = "remind me in 10 minutes to drink water"
        val commands = parser.parse(input)
        assertEquals(1, commands.size)
        val cmd = commands[0]
        assertEquals(CommandType.SET_REMINDER, cmd.type)
        assertEquals("drink water", cmd.messageBody)
        assertEquals(false, cmd.missingTime)
        assert(cmd.formattedTime?.contains("10") == true)
    }

    @Test
    fun testReminderMissingTime() {
        val input = "remind me to study"
        val commands = parser.parse(input)
        assertEquals(1, commands.size)
        val cmd = commands[0]
        assertEquals(CommandType.SET_REMINDER, cmd.type)
        assertEquals("study", cmd.messageBody)
        assertEquals(true, cmd.missingTime)
    }

    @Test
    fun testReminderMissingMessage() {
        val input = "remind me at 6 pm"
        val commands = parser.parse(input)
        assertEquals(1, commands.size)
        val cmd = commands[0]
        assertEquals(CommandType.SET_REMINDER, cmd.type)
        assertEquals(true, cmd.missingMessage)
        assertEquals(false, cmd.missingTime)
    }

    @Test
    fun testReminderInvalidTime() {
        val input = "remind me at banana"
        val commands = parser.parse(input)
        assertEquals(1, commands.size)
        val cmd = commands[0]
        assertEquals(CommandType.SET_REMINDER, cmd.type)
        assertEquals(true, cmd.missingTime)
    }

    @Test
    fun testReminderAfterKeyword() {
        val input = "remind me after 30 minutes to take medicine"
        val commands = parser.parse(input)
        assertEquals(1, commands.size)
        val cmd = commands[0]
        assertEquals(CommandType.SET_REMINDER, cmd.type)
        assertEquals("take medicine", cmd.messageBody)
        assertEquals(false, cmd.missingTime)
        assert(cmd.triggerMillis != null)
    }

    @Test
    fun testReminderWithinKeyword() {
        val input = "remind me within 1 hour to check email"
        val commands = parser.parse(input)
        assertEquals(1, commands.size)
        val cmd = commands[0]
        assertEquals(CommandType.SET_REMINDER, cmd.type)
        assertEquals("check email", cmd.messageBody)
        assertEquals(false, cmd.missingTime)
    }

    @Test
    fun testReminderMissingUnit() {
        val input = "remind me in 30 to leave"
        val commands = parser.parse(input)
        assertEquals(1, commands.size)
        val cmd = commands[0]
        assertEquals(CommandType.SET_REMINDER, cmd.type)
        assertEquals("leave", cmd.messageBody)
        assertEquals(true, cmd.missingTimeUnit)
    }
}
