package io.nekohasekai.libbox

open class BridgeOptions
open class BridgeSession
open class ConnectionOwner
open class LocalDNSTransport
open class Notification
open class OverrideOptions
open class PlatformUser
open class SetupOptions {
    var basePath: String = ""
    var workingPath: String = ""
    var tempPath: String = ""
}
open class ShellSession
open class SystemProxyStatus
open class TunOptions {
    var mtu: Int = 1500
}
open class WIFIState

open class NetworkInterface {
    var name: String = ""
    var index: Int = 0
    var mtu: Int = 1500
    var addresses: StringIterator? = null
    var type: Int = 0
}

interface CommandServerHandler {
    fun serviceStop()
    fun serviceReload()
    fun getSystemProxyStatus(): SystemProxyStatus?
    fun setSystemProxyEnabled(enabled: Boolean)
    fun triggerNativeCrash()
    fun writeDebugMessage(message: String?)
    fun connectSSHAgent(): Int
}

interface InterfaceUpdateListener
interface NeighborUpdateListener

interface NetworkInterfaceIterator {
    fun hasNext(): Boolean
    fun next(): NetworkInterface
}

interface StringIterator {
    fun len(): Int
    fun hasNext(): Boolean
    fun next(): String
}

interface PlatformInterface {
    fun localDNSTransport(): LocalDNSTransport?
    fun usePlatformAutoDetectInterfaceControl(): Boolean
    fun autoDetectInterfaceControl(fd: Int)
    fun openTun(options: TunOptions?): Int
    fun useProcFS(): Boolean
    fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String?,
        sourcePort: Int,
        destinationAddress: String?,
        destinationPort: Int
    ): ConnectionOwner
    fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?)
    fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?)
    fun getInterfaces(): NetworkInterfaceIterator
    fun underNetworkExtension(): Boolean
    fun includeAllNetworks(): Boolean
    fun readWIFIState(): WIFIState?
    fun clearDNSCache()
    fun sendNotification(notification: Notification?)
    fun cancelNotification(identifier: String?, typeID: Int)
    fun startNeighborMonitor(listener: NeighborUpdateListener?)
    fun closeNeighborMonitor(listener: NeighborUpdateListener?)
    fun registerMyInterface(name: String?)
    fun usePlatformShell(): Boolean
    fun checkPlatformShell()
    fun openShellSession(
        user: PlatformUser?,
        command: String?,
        environ: StringIterator?,
        term: String?,
        rows: Int,
        cols: Int
    ): ShellSession
    fun lookupUser(username: String?): PlatformUser
    fun lookupSFTPServer(): String
    fun readSystemSSHHostKey(): String
    fun tailscaleHostname(): String
    fun usePlatformBridge(): Boolean
    fun createBridge(options: BridgeOptions?): BridgeSession
}

open class CommandServer(
    val handler: CommandServerHandler,
    val platformInterface: PlatformInterface
) {
    open fun start() {}
    open fun startOrReloadService(configJson: String, options: OverrideOptions) {}
    open fun closeService() {}
    open fun close() {}
}

object Libbox {
    const val InterfaceTypeOther: Int = 0

    @JvmStatic
    fun setup(options: SetupOptions) {}

    @JvmStatic
    fun checkConfig(configJson: String) {}
}
