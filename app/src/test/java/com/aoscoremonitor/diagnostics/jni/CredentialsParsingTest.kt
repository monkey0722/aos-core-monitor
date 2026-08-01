package com.aoscoremonitor.diagnostics.jni

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialsParsingTest {

    @Test
    fun readsWhoTheProcessIs() {
        val credentials = parseCredentials(
            """
            {"pid":8421,"ppid":731,"pgid":8421,"sid":8421,
             "uid":{"real":10213,"effective":10213,"saved":10213},
             "gid":{"real":10213,"effective":10213,"saved":10213},
             "groups":[9997,3003,20213],
             "umask":"0077",
             "selinux_context":"u:r:untrusted_app:s0:c213,c256"}
            """.trimIndent()
        )

        assertEquals(8_421, credentials.pid)
        assertEquals(731, credentials.parentPid)
        assertEquals(8_421, credentials.session)
        assertEquals(10_213, credentials.user?.effective)
        assertTrue(credentials.user!!.allTheSame)
        assertEquals("0077", credentials.umask)
        // Sorted, because getgroups fills the array in whatever order the kernel holds it.
        assertEquals(listOf(3_003, 9_997, 20_213), credentials.supplementaryGroups)
    }

    @Test
    fun anAppUidIsSplitIntoItsAndroidUserAndItsAppId() {
        val credentials = parseCredentials("""{"uid":{"real":110213,"effective":110213,"saved":110213}}""")

        assertEquals(1, credentials.androidUserId)
        assertEquals(10_213, credentials.appId)
        assertTrue(credentials.isAppUid)
    }

    @Test
    fun aPlatformUidIsNotReportedAsAnApp() {
        // AID_SYSTEM is below the app range, and calling it "app ID 1000" would be a wrong reading
        // rather than a missing one.
        val credentials = parseCredentials("""{"uid":{"real":1000,"effective":1000,"saved":1000}}""")

        assertFalse(credentials.isAppUid)
    }

    @Test
    fun theSelinuxTypeIsPulledOutOfTheContext() {
        val credentials = parseCredentials(
            """{"selinux_context":"u:r:untrusted_app:s0:c213,c256,c512,c768"}"""
        )

        assertEquals("untrusted_app", credentials.selinuxType)
    }

    @Test
    fun aContextTheSandboxRefusedSaysSo() {
        val credentials = parseCredentials("""{"selinux_context_unavailable":"denied"}""")

        assertNull(credentials.selinuxContext)
        assertNull(credentials.selinuxType)
        assertEquals(Unavailable.Denied, credentials.selinuxUnavailable)
    }

    @Test
    fun capabilitySetsCarryBothTheirMaskAndTheirNames() {
        val credentials = parseCredentials(
            """
            {"capabilities":{
               "effective":{"hex":"0000000000000000","names":[]},
               "permitted":{"hex":"0000000000000000","names":[]},
               "bounding":{"hex":"0000000000002000","names":["CAP_NET_RAW"]}}}
            """.trimIndent()
        )

        val bounding = credentials.capabilities["bounding"]!!
        assertEquals("0000000000002000", bounding.hex)
        assertEquals(listOf("CAP_NET_RAW"), bounding.names)
        assertTrue(credentials.capabilities["effective"]!!.isEmpty)
        // A set the kernel did not print is absent rather than empty: "this kernel has no ambient
        // set" is not "the ambient set is empty".
        assertNull(credentials.capabilities["ambient"])
    }

    @Test
    fun theSeccompModeIsNamedRatherThanNumbered() {
        assertEquals(SeccompMode.Filter, parseCredentials("""{"seccomp":2}""").seccomp)
        assertEquals(SeccompMode.Strict, parseCredentials("""{"seccomp":1}""").seccomp)
        assertEquals(SeccompMode.Disabled, parseCredentials("""{"seccomp":0}""").seccomp)
        // Absent, not zero: a kernel that does not report the mode has not reported "no filter".
        assertEquals(SeccompMode.Unknown, parseCredentials("{}").seccomp)
    }

    @Test
    fun aSwitchThatWasNotReportedIsNotReportedAsOff() {
        val reported = parseCredentials("""{"no_new_privs":false,"dumpable":true}""")
        assertEquals(false, reported.noNewPrivs)
        assertEquals(true, reported.dumpable)

        val unreported = parseCredentials("""{"no_new_privs_unavailable":"error"}""")
        assertNull(unreported.noNewPrivs)
        assertNull(unreported.dumpable)
    }

    @Test
    fun aGroupListThatCouldNotBeReadIsNotAnEmptyOne() {
        val credentials = parseCredentials("""{"groups_unavailable":"error"}""")

        assertTrue(credentials.supplementaryGroups.isEmpty())
        assertEquals(Unavailable.Error, credentials.groupsUnavailable)
    }

    @Test
    fun theGroupsAnAppIsGrantedAreNamed() {
        assertEquals("inet", androidGroupName(3003))
        assertEquals("everybody", androidGroupName(9997))
        assertEquals("cache", androidGroupName(20213))
        assertNull(androidGroupName(4242))
    }
}
