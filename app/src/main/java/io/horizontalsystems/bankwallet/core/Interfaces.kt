package io.horizontalsystems.bankwallet.core

import android.text.SpannableString
import androidx.core.hardware.fingerprint.FingerprintManagerCompat
import com.google.gson.JsonObject
import io.horizontalsystems.bankwallet.core.managers.ServiceExchangeApi.HostType
import io.horizontalsystems.bankwallet.core.storage.AccountRecord
import io.horizontalsystems.bankwallet.entities.*
import io.horizontalsystems.bankwallet.entities.Currency
import io.horizontalsystems.bankwallet.lib.chartview.ChartView
import io.horizontalsystems.bankwallet.modules.balance.BalanceSortType
import io.horizontalsystems.bankwallet.modules.fulltransactioninfo.FullTransactionInfoModule
import io.horizontalsystems.bankwallet.modules.send.SendModule
import io.horizontalsystems.bankwallet.modules.transactions.CoinCode
import io.horizontalsystems.eoskit.EosKit
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import java.math.BigDecimal
import java.util.*
import javax.crypto.SecretKey

interface IAdapterManager {
    val adapterCreationObservable: Flowable<Wallet>
    val adaptersReadyObservable: Flowable<Unit>
    fun preloadAdapters()
    fun refresh()
    fun stopKits()
    fun getAdapterForWallet(wallet: Wallet): IAdapter?
    fun getTransactionsAdapterForWallet(wallet: Wallet): ITransactionsAdapter?
    fun getBalanceAdapterForWallet(wallet: Wallet): IBalanceAdapter?
    fun getReceiveAdapterForWallet(wallet: Wallet): IReceiveAdapter?
}

interface ILocalStorage {
    var currentLanguage: String?
    var isBackedUp: Boolean
    var isFingerprintEnabled: Boolean
    var sendInputType: SendModule.InputType?
    var isLightModeOn: Boolean
    var iUnderstand: Boolean
    var baseCurrencyCode: String?
    var blockTillDate: Long?
    var failedAttempts: Int?
    var lockoutUptime: Long?
    var baseBitcoinProvider: String?
    var baseEthereumProvider: String?
    var baseDashProvider: String?
    var baseBinanceProvider: String?
    var baseEosProvider: String?
    var syncMode: SyncMode
    var sortType: BalanceSortType
    var chartMode: ChartView.Mode

    fun clear()
}

interface ISecuredStorage {
    val authData: AuthData?
    val savedPin: String?
    fun savePin(pin: String)
    fun removePin()
    fun pinIsEmpty(): Boolean
}

interface IAccountManager {
    val isAccountsEmpty: Boolean
    val accounts: List<Account>
    val accountsFlowable: Flowable<List<Account>>
    val deleteAccountObservable: Flowable<String>

    fun account(coinType: CoinType): Account?
    fun preloadAccounts()
    fun create(account: Account)
    fun update(account: Account)
    fun delete(id: String)
    fun clear()
    fun clearAccounts()
}

interface IBackupManager {
    val nonBackedUpCount: Int
    val nonBackedUpCountFlowable: Flowable<Int>
    fun setIsBackedUp(id: String)
}

interface IAccountCreator {
    fun createRestoredAccount(accountType: AccountType, syncMode: SyncMode?, createDefaultWallets: Boolean): Account
    fun createNewAccount(defaultAccountType: DefaultAccountType, createDefaultWallets: Boolean): Account
}

interface IAccountFactory {
    fun account(type: AccountType, backedUp: Boolean, defaultSyncMode: SyncMode?): Account
}

interface IWalletFactory {
    fun wallet(coin: Coin, account: Account, syncMode: SyncMode?): Wallet
}

interface IWalletStorage {
    fun wallets(accounts: List<Account>): List<Wallet>
    fun save(wallets: List<Wallet>)
}

interface IPredefinedAccountTypeManager {
    val allTypes: List<IPredefinedAccountType>
    fun account(predefinedAccountType: IPredefinedAccountType): Account?
    fun createAccount(predefinedAccountType: IPredefinedAccountType): Account?
    fun createAllAccounts()
}

interface IPredefinedAccountType {
    val title: Int // resource id
    val coinCodes: Int // resource id
    val defaultAccountType: DefaultAccountType
    fun supports(accountType: AccountType): Boolean
}

sealed class DefaultAccountType {
    class Mnemonic(val wordsCount: Int) : DefaultAccountType()
    class Eos : DefaultAccountType()
}

interface IRandomProvider {
    fun getRandomIndexes(count: Int): List<Int>
}

interface INetworkManager {
    fun getRateStats(hostType: HostType, coinCode: String, currency: String, chunk: String): Flowable<RateStatData>
    fun getRateByDay(hostType: HostType, coinCode: String, currency: String, timestamp: Long): Single<BigDecimal>
    fun getRateByHour(hostType: HostType, coinCode: String, currency: String, timestamp: Long): Single<BigDecimal>
    fun getLatestRateData(hostType: HostType, currency: String): Single<LatestRateData>
    fun getTransaction(host: String, path: String): Flowable<JsonObject>
    fun getTransactionWithPost(host: String, path: String, body: Map<String, Any>): Flowable<JsonObject>
    fun ping(host: String, url: String): Flowable<Any>
}

interface IEncryptionManager {
    fun encrypt(data: String): String
    fun decrypt(data: String): String
    fun getCryptoObject(): FingerprintManagerCompat.CryptoObject?
}

interface IKeyStoreManager {
    val isKeyInvalidated: Boolean
    val isUserNotAuthenticated: Boolean

    fun createKey(): SecretKey
    fun getKey(): SecretKey
    fun removeKey()
}

interface IKeyProvider {
    fun getKey(): SecretKey
}

interface IClipboardManager {
    fun copyText(text: String)
    fun getCopiedText(): String
    val hasPrimaryClip: Boolean
}

interface ICurrencyManager {
    val baseCurrency: Currency
    val baseCurrencyUpdatedSignal: Observable<Unit>
    val currencies: List<Currency>
    fun setBaseCurrency(code: String)
}

interface ITransactionDataProviderManager {
    val baseProviderUpdatedSignal: Observable<Unit>

    fun providers(coin: Coin): List<FullTransactionInfoModule.Provider>
    fun baseProvider(coin: Coin): FullTransactionInfoModule.Provider
    fun setBaseProvider(name: String, coin: Coin)

    fun bitcoin(name: String): FullTransactionInfoModule.BitcoinForksProvider
    fun dash(name: String): FullTransactionInfoModule.BitcoinForksProvider
    fun bitcoinCash(name: String): FullTransactionInfoModule.BitcoinForksProvider
    fun ethereum(name: String): FullTransactionInfoModule.EthereumForksProvider
    fun binance(name: String): FullTransactionInfoModule.BinanceProvider
    fun eos(name: String): FullTransactionInfoModule.EosProvider
}

interface IWordsManager {
    var isBackedUp: Boolean
    var backedUpSignal: PublishSubject<Unit>

    fun validate(words: List<String>)
    fun generateWords(count: Int = 12): List<String>
}

interface ILanguageManager {
    var currentLanguage: Locale
    val availableLanguages: List<Locale>
}

sealed class AdapterState {
    object Synced : AdapterState()
    class Syncing(val progress: Int, val lastBlockDate: Date?) : AdapterState()
    object NotSynced : AdapterState()
    object NotReady : AdapterState()
}

interface IEthereumKitManager {
    val ethereumKit: EthereumKit?

    fun ethereumKit(wallet: Wallet): EthereumKit
    fun unlink()
}

interface IEosKitManager {
    val eosKit: EosKit?
    fun eosKit(wallet: Wallet): EosKit

    fun unlink()
}

interface ITransactionsAdapter {
    val confirmationsThreshold: Int
    val lastBlockHeight: Int?
    val lastBlockHeightUpdatedFlowable: Flowable<Unit>

    fun getTransactions(from: Pair<String, Int>? = null, limit: Int): Single<List<TransactionRecord>>
    val transactionRecordsFlowable: Flowable<List<TransactionRecord>>
}

interface IBalanceAdapter {
    val state: AdapterState
    val stateUpdatedFlowable: Flowable<Unit>

    val balance: BigDecimal
    val balanceUpdatedFlowable: Flowable<Unit>

}

interface IReceiveAdapter {
    val receiveAddress: String
}

interface ISendBitcoinAdapter {
    fun availableBalance(feeRate: Long, address: String?): BigDecimal
    fun fee(amount: BigDecimal, feeRate: Long, address: String?): BigDecimal
    fun validate(address: String)
    fun send(amount: BigDecimal, address: String, feeRate: Long): Single<Unit>
}

interface ISendDashAdapter {
    fun availableBalance(address: String?): BigDecimal
    fun fee(amount: BigDecimal, address: String?): BigDecimal
    fun validate(address: String)
    fun send(amount: BigDecimal, address: String): Single<Unit>
}

interface ISendEthereumAdapter {
    val ethereumBalance: BigDecimal

    fun availableBalance(gasPrice: Long): BigDecimal
    fun fee(gasPrice: Long): BigDecimal
    fun validate(address: String)
    fun send(amount: BigDecimal, address: String, gasPrice: Long): Single<Unit>
}

interface ISendBinanceAdapter {
    val availableBalance: BigDecimal
    val availableBinanceBalance: BigDecimal
    val fee: BigDecimal

    fun validate(address: String)
    fun send(amount: BigDecimal, address: String, memo: String?): Single<Unit>
}

interface ISendEosAdapter {
    val availableBalance: BigDecimal

    fun validate(account: String)
    fun send(amount: BigDecimal, account: String, memo: String?): Single<Unit>
}

interface IAdapter {
    fun start()
    fun stop()
    fun refresh()

    val debugInfo: String
}

interface ISystemInfoManager {
    val appVersion: String
    val isSystemLockOff: Boolean
    val hasFingerprintSensor: Boolean
    val hasEnrolledFingerprints: Boolean
}

interface IPinManager {
    val isPinSet: Boolean

    fun store(pin: String)
    fun validate(pin: String): Boolean
    fun clear()
}

interface ILockManager {
    val lockStateUpdatedSignal: PublishSubject<Unit>
    var isLocked: Boolean
    fun onUnlock()
}

interface IAppConfigProvider {
    val ipfsId: String
    val ipfsMainGateway: String
    val ipfsFallbackGateway: String
    val infuraProjectId: String?
    val infuraProjectSecret: String?
    val fiatDecimal: Int
    val maxDecimal: Int
    val testMode: Boolean
    val localizations: List<String>
    val currencies: List<Currency>
    val defaultCoinCodes: List<String>
    val coins: List<Coin>
    val predefinedAccountTypes: List<IPredefinedAccountType>
}

interface IOneTimerDelegate {
    fun onFire()
}

interface IRateStorage {
    fun latestRateObservable(coinCode: CoinCode, currencyCode: String): Flowable<Rate>
    fun rateSingle(coinCode: CoinCode, currencyCode: String, timestamp: Long): Single<Rate>
    fun save(rate: Rate)
    fun saveLatest(rate: Rate)
    fun deleteAll()
}

interface IAccountsStorage {
    val isAccountsEmpty: Boolean

    fun allAccounts(): List<Account>
    fun save(account: Account)
    fun update(account: Account)
    fun delete(id: String)
    fun getNonBackedUpCount(): Flowable<Int>
    fun clear()
    fun getDeleted(): List<AccountRecord>
    fun clearDeleted()
}

interface IEnabledWalletStorage {
    val enabledWallets: List<EnabledWallet>
    fun save(coins: List<EnabledWallet>)
    fun deleteAll()
}

interface ILockoutManager {
    fun didFailUnlock()
    fun dropFailedAttempts()

    val currentState: LockoutState
}

interface IUptimeProvider {
    val uptime: Long
}

interface ILockoutUntilDateFactory {
    fun lockoutUntilDate(failedAttempts: Int, lockoutTimestamp: Long, uptime: Long): Date?
}

interface ICurrentDateProvider {
    val currentDate: Date
}

interface IWalletManager {
    val wallets: List<Wallet>
    val walletsUpdatedSignal: Observable<Unit>
    fun wallet(coin: Coin): Wallet?

    fun loadWallets()
    fun enable(wallets: List<Wallet>)
    fun clear()
}

interface IAppNumberFormatter {
    fun format(coinValue: CoinValue, explicitSign: Boolean = false, realNumber: Boolean = false): String?
    fun format(currencyValue: CurrencyValue, showNegativeSign: Boolean = true, trimmable: Boolean = false, canUseLessSymbol: Boolean = true, shorten: Boolean = false): String?
    fun formatForTransactions(coinValue: CoinValue): String?
    fun formatForTransactions(currencyValue: CurrencyValue, isIncoming: Boolean): SpannableString
    fun format(value: Double): String
    fun shortenNumber(value: Long): String
}

interface IFeeRateProvider {
    fun feeRate(priority: FeeRatePriority): Long
}

interface IAddressParser {
    fun parse(paymentAddress: String): AddressData
}

enum class FeeRatePriority(val value: Int) {
    LOWEST(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    HIGHEST(4);

    companion object {
        fun valueOf(value: Int): FeeRatePriority = values().find { it.value == value } ?: MEDIUM
    }
}
