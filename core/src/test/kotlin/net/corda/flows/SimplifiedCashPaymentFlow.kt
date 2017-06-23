package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.InsufficientBalanceException
import net.corda.core.contracts.TransactionType
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*

/**
 * Initiates a flow that sends cash to a recipient.
 *
 * @param amount the amount of a currency to pay to the recipient.
 * @param recipient the party to pay the currency to.
 * @param issuerConstraint if specified, the payment will be made using only cash issued by the given parties.
 */
@net.corda.core.flows.StartableByRPC
open class SimplifiedCashPaymentFlow(
        val amount: net.corda.core.contracts.Amount<Currency>,
        val recipient: net.corda.core.identity.Party,
        progressTracker: net.corda.core.utilities.ProgressTracker,
        val issuerConstraint: Set<net.corda.core.identity.Party>? = null) : net.corda.flows.AbstractCashFlow<SignedTransaction>(progressTracker) {
    /** A straightforward constructor that constructs spends using cash states of any issuer. */
    constructor(amount: net.corda.core.contracts.Amount<Currency>, recipient: net.corda.core.identity.Party) : this(amount, recipient, net.corda.flows.AbstractCashFlow.Companion.tracker())

    @co.paralleluniverse.fibers.Suspendable
    override fun call(): net.corda.core.transactions.SignedTransaction {
        progressTracker.currentStep = net.corda.flows.AbstractCashFlow.Companion.GENERATING_TX
        val builder: net.corda.core.transactions.TransactionBuilder = net.corda.core.contracts.TransactionType.General.Builder(null as Party?)
        // TODO: Have some way of restricting this to states the caller controls
        val (spendTX, keysForSigning) = try {
            serviceHub.vaultService.generateSpend(
                    builder,
                    amount,
                    recipient,
                    issuerConstraint)
        } catch (e: net.corda.core.contracts.InsufficientBalanceException) {
            throw net.corda.flows.CashException("Insufficient cash for spend: ${e.message}", e)
        }

        progressTracker.currentStep = net.corda.flows.AbstractCashFlow.Companion.SIGNING_TX
        val tx = serviceHub.signInitialTransaction(spendTX, keysForSigning)

        progressTracker.currentStep = net.corda.flows.AbstractCashFlow.Companion.FINALISING_TX
        finaliseTx(setOf(recipient), tx, "Unable to notarise spend")
        return tx
    }
}