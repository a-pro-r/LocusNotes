package com.beakoninc.locusnotes.data.takeout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** Row shapes taken from a real Google Takeout "Saved" export. */
class TakeoutParserTest {

    @Test
    fun `parses place rows and skips header and blank filler row`() {
        val csv = """
            Title,Note,URL,Tags,Comment
            ,,,,
            Badwater,,https://www.google.com/maps/place/Badwater/data=!4m2!3m1!1s0x80c71f11e13eeafd:0xfb29ec2555008b15,,
            Dante's View,,https://www.google.com/maps/place/Dante's+View/data=!4m2!3m1!1s0x80c71e6785b673ff:0xa94a69f36db606a5,,
        """.trimIndent()

        val places = TakeoutParser.parseCsv("California", csv)

        assertEquals(2, places.size)
        assertEquals("Badwater", places[0].title)
        assertEquals("California", places[0].listName)
        assertFalse(places[0].hasCoordinates) // opaque feature id, needs geocoding
        assertTrue(places[0].isPlace)
    }

    @Test
    fun `dropped pin with quoted DMS title and note yields coordinates`() {
        val csv = "Title,Note,URL,Tags,Comment\n" +
                ",,,,\n" +
                "\"36°57'18.0\"\"N 111°53'35.9\"\"W\",White pocket AZ. Need 4X4 4wd not AWD. Do not go while raining.  NO stopping while on sands,\"https://www.google.com/maps/search/36.955011,-111.893296\",,\n"

        val places = TakeoutParser.parseCsv("Want to go", csv)

        assertEquals(1, places.size)
        val pin = places[0]
        assertEquals("36°57'18.0\"N 111°53'35.9\"W", pin.title)
        assertTrue(pin.note.startsWith("White pocket AZ"))
        assertEquals(36.955011, pin.latitude!!, 1e-9)
        assertEquals(-111.893296, pin.longitude!!, 1e-9)
    }

    @Test
    fun `movie book and image saves are not places`() {
        val csv = """
            Title,Note,URL,Tags,Comment
            ,,,,
            Meet Joe Black,,https://www.google.com,,
            The Hollow Man,,https://www.google.com/books/edition/The_Hollow_Man/frw-DgAAQBAJ,,
            Meme Template,,http://lenzografy.com/wp-content/uploads/2017/05/shot.jpg,,
            Colorado City,FLDS church - fundamentalist cult,https://www.google.com/maps/place/Colorado+City/data=!4m2!3m1!1s0x80cb20481124fce1:0x29e37db4075fd3f0,,
        """.trimIndent()

        val (places, others) = TakeoutParser.parseCsv("Default list", csv).partition { it.isPlace }

        assertEquals(1, places.size)
        assertEquals("Colorado City", places[0].title)
        assertEquals("FLDS church - fundamentalist cult", places[0].note)
        assertEquals(3, others.size)
    }

    @Test
    fun `quoted titles with commas parse as one field`() {
        val csv = "Title,Note,URL,Tags,Comment\n" +
                "\"Sorry, Baby\",,https://www.google.com,,\n"

        val places = TakeoutParser.parseCsv("Default list(1)", csv)

        assertEquals(1, places.size)
        assertEquals("Sorry, Baby", places[0].title)
    }

    @Test
    fun `coordinate extraction covers the URL variants`() {
        assertEquals(
            36.955011 to -111.893296,
            TakeoutParser.extractCoordinates("https://www.google.com/maps/search/36.955011,-111.893296")
        )
        assertEquals(
            40.7484 to -73.9857,
            TakeoutParser.extractCoordinates("https://www.google.com/maps/place/x/data=!3d40.7484!4d-73.9857")
        )
        assertEquals(
            48.8584 to 2.2945,
            TakeoutParser.extractCoordinates("https://www.google.com/maps/@48.8584,2.2945,15z")
        )
        assertNull(TakeoutParser.extractCoordinates("https://www.google.com/maps/place/Badwater/data=!4m2!3m1!1s0x80c7:0xfb29"))
        assertNull(TakeoutParser.extractCoordinates(""))
        // Out-of-range numbers (e.g. ids that look like coordinates) are rejected
        assertNull(TakeoutParser.extractCoordinates("https://www.google.com/maps/search/365.0,-1118.9"))
    }
}
