package com.htoc.lab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.cardemulation.CardEmulation
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.htoc.lab.utils.NfcService
import java.nio.ByteBuffer

class NfcFragment : Fragment() {

    companion object {
        private const val TAG = "NfcFragment"
        private const val MAX_CHUNK_SIZE = 200
        private const val AID_HEX = "F0394148148100"

        private val SELECT_APDU = buildApdu(0x00, 0xA4, 0x04, 0x00, *hexToBytes(AID_HEX), 0x00)
        private val GET_RESPONSE_APDU = byteArrayOf(0x00, 0xC0.toByte(), 0x00, 0x00, 0x00)

        private fun buildApdu(vararg bytes: Int): ByteArray =
            bytes.map { it.toByte() }.toByteArray()

        private fun hexToBytes(hex: String): IntArray {
            val len = hex.length
            val data = IntArray(len / 2)
            for (i in 0 until len step 2) {
                data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                        Character.digit(hex[i + 1], 16))
            }
            return data
        }
    }

    private var isSending = false
    private var nfcAdapter: NfcAdapter? = null

    private lateinit var inputMessage: TextInputEditText
    private lateinit var btnSend: MaterialButton
    private lateinit var btnReceive: MaterialButton
    private lateinit var statusCard: MaterialCardView
    private lateinit var statusTitle: TextView
    private lateinit var statusText: TextView
    private lateinit var statusDetail: TextView

    private val nfcReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val message = intent.getStringExtra(NfcService.EXTRA_MESSAGE) ?: return
            Log.d(TAG, "Broadcast received: $message")
            activity?.runOnUiThread {
                showStatus(
                    getString(R.string.nfc_status_received_title),
                    message,
                    getString(R.string.nfc_status_received_hint)
                )
                Snackbar.make(requireView(), R.string.nfc_received_snackbar, Snackbar.LENGTH_SHORT).show()
                resetButtons()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_nfc, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        inputMessage = view.findViewById(R.id.input_message)
        btnSend = view.findViewById(R.id.btn_send)
        btnReceive = view.findViewById(R.id.btn_receive)
        statusCard = view.findViewById(R.id.status_card)
        statusTitle = view.findViewById(R.id.status_title)
        statusText = view.findViewById(R.id.status_text)
        statusDetail = view.findViewById(R.id.status_detail)

        val manager = requireContext().getSystemService(Context.NFC_SERVICE) as NfcManager
        nfcAdapter = manager.defaultAdapter

        if (nfcAdapter == null) {
            btnSend.isEnabled = false
            btnReceive.isEnabled = false
            showStatus(
                getString(R.string.nfc_status_no_nfc_title),
                getString(R.string.nfc_status_no_nfc_text),
                null
            )
            return
        }

        btnSend.setOnClickListener { onSendClicked() }
        btnReceive.setOnClickListener { onReceiveClicked() }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(NfcService.ACTION_DATA_RECEIVED)
        requireContext().registerReceiver(nfcReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        if (isSending) {
            enableReaderMode()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(nfcReceiver)
        } catch (_: IllegalArgumentException) {}

        if (isSending) {
            disableReaderMode()
        }
    }

    private fun onSendClicked() {
        val message = inputMessage.text?.toString()?.trim()
        if (message.isNullOrEmpty()) {
            Snackbar.make(requireView(), R.string.nfc_empty_message, Snackbar.LENGTH_SHORT).show()
            return
        }
        if (nfcAdapter == null) return

        isSending = true
        btnSend.isEnabled = false
        btnReceive.isEnabled = false
        inputMessage.isEnabled = false

        showStatus(
            getString(R.string.nfc_status_sending_title),
            message,
            getString(R.string.nfc_status_sending_hint)
        )

        enableReaderMode()
    }

    private fun onReceiveClicked() {
        val message = inputMessage.text?.toString()?.trim()

        if (!message.isNullOrEmpty()) {
            NfcService.setResponseData(requireContext(), message.toByteArray(Charsets.UTF_8))
        }

        // Register AID routing for our HCE service
        registerHceService()

        btnSend.isEnabled = false
        btnReceive.isEnabled = false
        inputMessage.isEnabled = false

        showStatus(
            getString(R.string.nfc_status_receiving_title),
            getString(R.string.nfc_status_receiving_text),
            getString(R.string.nfc_status_receiving_hint)
        )

        Snackbar.make(requireView(), R.string.nfc_ready_to_receive, Snackbar.LENGTH_LONG).show()
    }

    private fun registerHceService() {
        try {
            val adapter = nfcAdapter ?: return
            val cardEmulation = CardEmulation.getInstance(adapter)
            val componentName = android.content.ComponentName(
                requireContext(),
                NfcService::class.java
            )

            // Explicitly start the HCE service to ensure it's bound
            try {
                val intent = android.content.Intent(requireContext(), NfcService::class.java)
                requireContext().startService(intent)
                Log.d(TAG, "HCE service start requested")
            } catch (e: Exception) {
                Log.w(TAG, "Could not start HCE service (expected for bound service): ${e.message}")
            }

            val registered = cardEmulation.registerAidsForService(
                componentName,
                CardEmulation.CATEGORY_OTHER,
                listOf(AID_HEX)
            )
            Log.d(TAG, "AID registration result: $registered")

            // Verify AIDs are registered
            val aids = cardEmulation.getAidsForService(componentName, CardEmulation.CATEGORY_OTHER)
            Log.d(TAG, "Registered AIDs: $aids")

            // Set as preferred service so system routes APDU to us
            val activity = activity
            if (activity != null) {
                val preferred = cardEmulation.setPreferredService(activity, componentName)
                Log.d(TAG, "Set preferred service result: $preferred")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register HCE AID", e)
        }
    }

    private fun unregisterHceService() {
        try {
            val adapter = nfcAdapter ?: return
            val cardEmulation = CardEmulation.getInstance(adapter)
            val componentName = android.content.ComponentName(
                requireContext(),
                NfcService::class.java
            )
            cardEmulation.removeAidsForService(componentName, CardEmulation.CATEGORY_OTHER)

            val activity = activity
            if (activity != null) {
                cardEmulation.unsetPreferredService(activity)
                Log.d(TAG, "Unset preferred service")
            }
            Log.d(TAG, "AID unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister HCE AID", e)
        }
    }

    private fun enableReaderMode() {
        try {
            nfcAdapter?.enableReaderMode(
                activity,
                { tag -> handleTagDiscovered(tag) },
                NfcAdapter.FLAG_READER_NFC_A or
                        NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                        NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable reader mode", e)
            activity?.runOnUiThread { resetButtons() }
        }
    }

    private fun disableReaderMode() {
        try {
            nfcAdapter?.disableReaderMode(activity)
        } catch (_: Exception) {}
    }

    private fun handleTagDiscovered(tag: android.nfc.Tag) {
        val techs = tag.techList.toList()
        val uid = tag.id.joinToString("") { "%02X".format(it) }
        Log.d(TAG, "Tag detected: uid=$uid techs=$techs")

        val isoDep = IsoDep.get(tag)
        if (isoDep == null) {
            // Not an IsoDep tag (e.g. physical MifareClassic card) — skip and keep waiting
            Log.d(TAG, "Skipping non-IsoDep tag: $techs")
            return
        }

        try {
            isoDep.connect()
            isoDep.timeout = 10000
            Log.d(TAG, "IsoDep connected")

            val selectResp = isoDep.transceive(SELECT_APDU)
            Log.d(TAG, "SELECT resp: ${selectResp.toHexString()}")
            if (!isSuccess(selectResp)) {
                Log.w(TAG, "SELECT failed")
                isoDep.close()
                return
            }

            val message = inputMessage.text?.toString()?.trim() ?: ""
            val msgBytes = message.toByteArray(Charsets.UTF_8)
            sendDataChunks(isoDep, msgBytes)
            Log.d(TAG, "Data sent")

            val response = tryReceiveResponse(isoDep)
            Log.d(TAG, "Response: $response")
            isoDep.close()

            activity?.runOnUiThread {
                if (response != null) {
                    showStatus(
                        getString(R.string.nfc_status_exchange_title),
                        response,
                        getString(R.string.nfc_status_exchange_hint)
                    )
                } else {
                    showStatus(
                        getString(R.string.nfc_status_sent_title),
                        message,
                        getString(R.string.nfc_status_sent_hint)
                    )
                }
                Snackbar.make(requireView(), R.string.nfc_transfer_complete, Snackbar.LENGTH_SHORT).show()
                resetButtons()
            }

        } catch (e: Exception) {
            Log.e(TAG, "NFC error", e)
            try { isoDep.close() } catch (_: Exception) {}
            activity?.runOnUiThread {
                showStatus(
                    getString(R.string.nfc_status_error_title),
                    e.message ?: "Unknown error",
                    null
                )
                resetButtons()
            }
        }
    }

    private fun sendDataChunks(isoDep: IsoDep, data: ByteArray) {
        val totalChunks = (data.size + MAX_CHUNK_SIZE - 1) / MAX_CHUNK_SIZE

        for (i in 0 until totalChunks) {
            val offset = i * MAX_CHUNK_SIZE
            val chunkSize = minOf(MAX_CHUNK_SIZE, data.size - offset)
            val chunk = data.copyOfRange(offset, offset + chunkSize)

            val payload = ByteBuffer.allocate(6 + chunkSize)
            payload.putInt(data.size)
            payload.put(i.toByte())
            payload.put(totalChunks.toByte())
            payload.put(chunk)

            val writeCmd = buildWriteApdu(payload.array())
            val resp = isoDep.transceive(writeCmd)
            if (!isSuccess(resp)) {
                throw Exception("WRITE chunk $i failed: ${resp.toHexString()}")
            }
        }
    }

    private fun tryReceiveResponse(isoDep: IsoDep): String? {
        val resp = isoDep.transceive(GET_RESPONSE_APDU)
        Log.d(TAG, "GET RESPONSE: ${resp.toHexString()} (${resp.size} bytes)")
        if (!isSuccess(resp) || resp.size <= 2) return null
        return String(resp.copyOfRange(0, resp.size - 2), Charsets.UTF_8)
    }

    private fun buildWriteApdu(data: ByteArray): ByteArray {
        val apdu = ByteArray(5 + data.size)
        apdu[0] = 0x00
        apdu[1] = 0xD6.toByte()
        apdu[2] = 0x00
        apdu[3] = 0x00
        apdu[4] = data.size.toByte()
        data.copyInto(apdu, 5)
        return apdu
    }

    private fun isSuccess(resp: ByteArray): Boolean {
        if (resp.size < 2) return false
        return resp[resp.size - 2] == 0x90.toByte() && resp[resp.size - 1] == 0x00.toByte()
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02X".format(it) }

    private fun showStatus(title: String, text: String, detail: String?) {
        statusCard.visibility = View.VISIBLE
        statusTitle.text = title
        statusText.text = text
        statusDetail.text = detail ?: ""
        statusDetail.visibility = if (detail.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun resetButtons() {
        isSending = false
        unregisterHceService()
        btnSend.isEnabled = true
        btnReceive.isEnabled = true
        inputMessage.isEnabled = true
    }
}
