package dev.bluehouse.enablevolte

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.telephony.CarrierConfigManager
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.IllegalStateException

class SIM1VoLTEConfigToggleQSTileService : VoLTEConfigToggleQSTileService(0)
class SIM2VoLTEConfigToggleQSTileService : VoLTEConfigToggleQSTileService(1)

open class VoLTEConfigToggleQSTileService(private val simSlotIndex: Int) : TileService() {
    private val TAG = "SIM${simSlotIndex}VoLTEConfigToggleQSTileService"

    init {
        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")
    }

    private val moder: SubscriptionModer? get() {
        val carrierModer = CarrierModer(this.applicationContext)

        try {
            if (checkShizukuPermission(0) == ShizukuStatus.GRANTED && carrierModer.deviceSupportsIMS) {
                carrierModer.subscriptions
                val sub = carrierModer.getActiveSubscriptionInfoForSimSlotIndex(this.simSlotIndex)
                    ?: return null
                return SubscriptionModer(sub.subscriptionId)
            }
        } catch (_: IllegalStateException) {}
        return null
    }

    private val volteEnabled: Boolean? get() {
        /*
         * true: VoLTE enabled
         * false: VoLTE disabled
         * null: cannot determine status (Shizuku not running or permission not granted, SIM slot not active, ...)
         */
        val moder = this.moder ?: return null
        try {
            return moder.isVoLteConfigEnabled
        } catch (_: IllegalStateException) {}
        return null
    }

    override fun onTileAdded() {
        super.onTileAdded()
        if (this.volteEnabled == null) {
            qsTile.state = Tile.STATE_UNAVAILABLE
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile.state = when (this.volteEnabled) {
            true -> Tile.STATE_ACTIVE
            false -> Tile.STATE_INACTIVE
            null -> Tile.STATE_UNAVAILABLE
        }
        qsTile.subtitle = getString(
            when (this.volteEnabled) {
                true -> R.string.enabled
                false -> R.string.disabled
                null -> R.string.unknown
            },
        )
        qsTile.updateTile()
    }

    private fun toggleVoLTEStatus() {
        val moder = this.moder ?: return
        val volteEnabled = this.volteEnabled ?: return
        moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, !volteEnabled)
        moder.restartIMSRegistration()
        qsTile.state = if (volteEnabled) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        qsTile.subtitle = getString(if (volteEnabled) R.string.disabled else R.string.enabled)
        qsTile.updateTile()
    }

    // Called when the user taps on your tile in an active or inactive state.
    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { toggleVoLTEStatus() }
        } else {
            toggleVoLTEStatus()
        }
    }
}
