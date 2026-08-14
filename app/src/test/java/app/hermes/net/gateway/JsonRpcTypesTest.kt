package app.hermes.net.gateway

import org.junit.Assert.*
import org.junit.Test

class JsonRpcTypesTest {

    @Test
    fun `parse response with id`() {
        val raw = """{"jsonrpc":"2.0","id":1,"result":{"session_id":"test123"}}"""
        val msg = JsonRpcTypes.parse(raw)
        
        assertTrue(msg is JsonRpcTypes.Response)
        assertEquals(1L, msg.id)
        assertNotNull(msg.result)
        assertEquals("test123", msg.result?.optString("session_id"))
        assertNull(msg.error)
    }

    @Test
    fun `parse notification without id`() {
        val raw = """{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","session_id":"abc"}}"""
        val msg = JsonRpcTypes.parse(raw)
        
        assertTrue(msg is JsonRpcTypes.Notification)
        assertEquals("event", msg.method)
        assertNotNull(msg.params)
        assertEquals("gateway.ready", msg.params.optString("type"))
    }

    @Test
    fun `parse error response`() {
        val raw = """{"jsonrpc":"2.0","id":5,"error":{"code":-32601,"message":"Method not found"}}"""
        val msg = JsonRpcTypes.parse(raw)
        
        assertTrue(msg is JsonRpcTypes.Response)
        assertEquals(5L, msg.id)
        assertNull(msg.result)
        assertNotNull(msg.error)
        assertEquals(-32601, msg.error?.code)
        assertEquals("Method not found", msg.error?.message)
    }

    @Test
    fun `parse unparseable json returns ServerError`() {
        val raw = "not valid json"
        val msg = JsonRpcTypes.parse(raw)
        
        assertTrue(msg is JsonRpcTypes.ServerError)
        assertEquals("unparseable json", msg.message)
    }
}
