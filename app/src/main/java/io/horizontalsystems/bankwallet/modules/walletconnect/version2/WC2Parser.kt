package io.horizontalsystems.bankwallet.modules.walletconnect.version2

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.walletconnect.sign.client.Sign
import io.horizontalsystems.bankwallet.modules.walletconnect.request.signmessage.SignMessage
import io.horizontalsystems.bankwallet.modules.walletconnect.session.v2.WCAccountData
import io.horizontalsystems.ethereumkit.core.hexStringToByteArray
import io.horizontalsystems.ethereumkit.models.Chain

object WC2Parser {

    fun getChainId(chain: String): Int? {
        val splitted = chain.split(":")
        if (splitted.size >= 2) {
            return splitted[1].toIntOrNull()
        }
        return null
    }

    fun parseTransactionRequest(
        request: Sign.Model.PendingRequest,
        address: String,
        dAppName: String
    ): WC2Request? {
        val params = JsonParser.parseString(request.params).asJsonArray

        when (request.method) {
            "eth_sendTransaction" -> {
                val transaction =
                    Gson().fromJson(params.first(), WC2EthereumTransaction::class.java)
                return WC2SendEthereumTransactionRequest(
                    request.requestId,
                    request.topic,
                    dAppName,
                    transaction
                )
            }
            "personal_sign" -> {
                val dataString = params.firstOrNull { it.asString != address }?.asString ?: ""
                val data = hexStringToUtf8String(dataString)
                return WC2SignMessageRequest(
                    request.requestId,
                    request.topic,
                    dAppName,
                    dataString,
                    SignMessage.PersonalMessage(data)
                )
            }
            "eth_sign" -> {
                val dataString = params.firstOrNull { it.asString != address }?.asString ?: ""
                val data = hexStringToUtf8String(dataString)
                return WC2SignMessageRequest(
                    request.requestId,
                    request.topic,
                    dAppName,
                    dataString,
                    SignMessage.Message(data)
                )
            }
            "eth_signTypedData" -> {
                val dataString = params.firstOrNull { it.isJsonObject }?.asJsonObject ?: return null
                val domain = dataString.get("domain")?.asJsonObject?.get("name")?.asString
                val message = SignMessage.TypedMessage(dataString.toString(), domain)
                return WC2SignMessageRequest(
                    request.requestId,
                    request.topic,
                    dAppName,
                    dataString.toString(),
                    message
                )
            }
        }
        return null
    }

    private fun hexStringToUtf8String(hexString: String) = try {
        String(hexString.hexStringToByteArray())
    } catch (_: Throwable) {
        hexString
    }

    fun getAccountData(string: String): WCAccountData? {
        val chunks = string.split(":")
        if (chunks.size < 2) {
            return null
        }

        val chainId = chunks[1].toIntOrNull() ?: return null
        val chain = Chain.values().firstOrNull { it.id == chainId }
        val address: String? = when {
            chunks.size >= 3 -> chunks[2]
            else -> null
        }

        return chain?.let {
            WCAccountData(eip = chunks[0], chain = chain, address = address)
        }
    }

}
