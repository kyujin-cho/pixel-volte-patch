package dev.bluehouse.enablevolte

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.IllegalStateException

class SIM1IMSStatusQSTileService : IMSStatusQSTileService(0)
class SIM2IMSStatusQSTileService : IMSStatusQSTileService(1)

open class IMSStatusQSTileService(private val simSlotIndex: Int) : TileService() {
    private val TAG = "SIM${simSlotIndex}IMSStatusQSTileService"

    init {
        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")
    }

    private val moder: SubscriptionModer? get() {
        val carrierModer = CarrierModer(this.applicationContext)

        try {
            if (checkShizukuPermission(0) == ShizukuStatus.GRANTED && carrierModer.deviceSupportsIMS) {
                val sub = carrierModer.getActiveSubscriptionInfoForSimSlotIndex(this.simSlotIndex)
                    ?: return null
                return SubscriptionModer(sub.subscriptionId)
            }
        } catch (_: IllegalStateException) {}
        return null
    }
    private val imsActivated: Boolean? get() {
        /*
         * true: VoLTE enabled
         * false: VoLTE disabled
         * null: cannot determine status (Shizuku not running or permission not granted, SIM slot not active, ...)
         */
        val moder = this.moder ?: return null
        try {
            return moder.isIMSRegistered
        } catch (_: IllegalStateException) {}
        return null
    }

    override fun onTileAdded() {
        super.onTileAdded()
        if (this.imsActivated == null) {
            qsTile.state = Tile.STATE_UNAVAILABLE
        }
    }

    private fun refreshStatus() {
        val imsActivated = this.imsActivated
        qsTile.state = when (imsActivated) {
            true -> Tile.STATE_ACTIVE
            false -> Tile.STATE_INACTIVE
            null -> Tile.STATE_UNAVAILABLE
        }
        qsTile.subtitle = getString(
            when (imsActivated) {
                true -> R.string.registered
                false -> R.string.unregistered
                null -> R.string.unknown
            },
        )
        qsTile.updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        this.refreshStatus()
    }

    override fun onClick() {
        super.onClick()
        moder?.restartIMSRegistration()
        this.refreshStatus()
    }
}
