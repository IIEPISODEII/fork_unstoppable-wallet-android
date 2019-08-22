package io.horizontalsystems.bankwallet.core.managers

import io.horizontalsystems.bankwallet.core.INetworkManager
import io.horizontalsystems.bankwallet.core.IRateStorage
import io.horizontalsystems.bankwallet.core.managers.ServiceExchangeApi.HostType
import io.horizontalsystems.bankwallet.entities.Rate
import io.horizontalsystems.bankwallet.entities.RateStatData
import io.horizontalsystems.bankwallet.modules.transactions.CoinCode
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import retrofit2.HttpException
import java.math.BigDecimal
import java.net.SocketTimeoutException

class RateManager(private val storage: IRateStorage, private val networkManager: INetworkManager) {

    private var disposables: CompositeDisposable = CompositeDisposable()
    private val latestRateFallbackThreshold = 60 * 10 // 10 minutes

    fun refreshLatestRates(coinCodes: List<String>, currencyCode: String) {
        disposables.clear()
        networkManager.getLatestRateData(HostType.MAIN, currencyCode)
                .onErrorResumeNext(networkManager.getLatestRateData(HostType.FALLBACK, currencyCode))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe({ latestRateData ->
                    coinCodes.forEach { coinCode ->
                        latestRateData.rates[coinCode]?.toBigDecimalOrNull()?.let {
                            val rate = Rate(coinCode, latestRateData.currency, it, latestRateData.timestamp, true)
                            storage.saveLatest(rate)
                        }
                    }
                }, {
                    //request failed
                })
                .let { disposables.add(it) }
    }

    private fun getLatestRateFallback(coinCode: CoinCode, currencyCode: String, timestamp: Long): Single<BigDecimal> {
        if (timestamp < ((System.currentTimeMillis() / 1000) - latestRateFallbackThreshold)) {
            return Single.error(Throwable())
        }

        return storage.latestRateObservable(coinCode, currencyCode)
                .firstOrError()
                .flatMap {
                    if (it.expired) {
                        Single.error(Throwable())
                    } else {
                        Single.just(it.value)
                    }
                }
    }

    fun rateValueObservable(coinCode: CoinCode, currencyCode: String, timestamp: Long): Single<BigDecimal> {
        return storage.rateSingle(coinCode, currencyCode, timestamp)
                .map { it.value }
                .onErrorResumeNext(
                        getLatestRateFallback(coinCode, currencyCode, timestamp)
                                .doOnSuccess {
                                    getRateFromNetwork(coinCode, currencyCode, timestamp)
                                            .subscribeOn(Schedulers.io())
                                            .subscribe({ /* success */ }, { /* request failed */ })
                                }
                                .onErrorResumeNext(getRateFromNetwork(coinCode, currencyCode, timestamp))
                )
    }

    private fun getRateFromNetwork(coinCode: CoinCode, currencyCode: String, timestamp: Long): Single<BigDecimal> {
        return networkManager.getRateByHour(HostType.MAIN, coinCode, currencyCode, timestamp)
                .onErrorResumeNext { throwable: Throwable ->
                    when (throwable) {
                        is SocketTimeoutException ->
                            networkManager.getRateByHour(HostType.FALLBACK, coinCode, currencyCode, timestamp)
                                    .onErrorResumeNext(networkManager.getRateByDay(HostType.FALLBACK, coinCode, currencyCode, timestamp))
                        is HttpException ->
                            networkManager.getRateByDay(HostType.MAIN, coinCode, currencyCode, timestamp)
                                    .onErrorResumeNext(networkManager.getRateByDay(HostType.FALLBACK, coinCode, currencyCode, timestamp))
                        else -> Single.error(Throwable())
                    }
                }
                .doOnSuccess { rateFromNetwork ->
                    storage.save(Rate(coinCode, currencyCode, rateFromNetwork, timestamp, false))
                }
    }

    fun getRateStats(coinCode: String, currency: String, chunk: String): Flowable<RateStatData> {
        return networkManager
                .getRateStats(HostType.MAIN, coinCode, currency, chunk)
                .onErrorResumeNext(networkManager.getRateStats(HostType.FALLBACK, coinCode, currency, chunk))
    }

    fun clear() {
        disposables.clear()
        storage.deleteAll()
    }
}
