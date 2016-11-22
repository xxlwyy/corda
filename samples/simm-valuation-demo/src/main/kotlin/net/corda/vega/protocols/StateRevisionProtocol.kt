package net.corda.vega.protocols

import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.seconds
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.UntrustworthyData
import net.corda.protocols.AbstractStateReplacementProtocol
import net.corda.vega.contracts.RevisionedState

/**
 * Protocol that generates an update on a mutable deal state and commits the resulting transaction reaching consensus
 * on the update between two parties
 */
object StateRevisionProtocol {
    data class Proposal<out T>(override val stateRef: StateRef,
                               override val modification: T,
                               override val stx: SignedTransaction) : AbstractStateReplacementProtocol.Proposal<T>

    class Requester<T>(curStateRef: StateAndRef<RevisionedState<T>>, val updatedData: T)
    : AbstractStateReplacementProtocol.Instigator<RevisionedState<T>, T>(curStateRef, updatedData) {
        override fun assembleProposal(stateRef: StateRef, modification: T, stx: SignedTransaction): AbstractStateReplacementProtocol.Proposal<T>
                = Proposal(stateRef, modification, stx)

        override fun assembleTx(): Pair<SignedTransaction, List<CompositeKey>> {
            val state = originalState.state.data
            val tx = state.generateRevision(originalState.state.notary, originalState, updatedData)
            tx.setTime(serviceHub.clock.instant(), 30.seconds)
            tx.signWith(serviceHub.legalIdentityKey)

            val stx = tx.toSignedTransaction(false)
            return Pair(stx, state.participants)
        }
    }

    class Receiver<T>(otherParty: Party, private val validate: (T) -> Boolean)
    : AbstractStateReplacementProtocol.Acceptor<T>(otherParty) {
        override fun verifyProposal(maybeProposal: UntrustworthyData<AbstractStateReplacementProtocol.Proposal<T>>)
                : AbstractStateReplacementProtocol.Proposal<T> {
            return maybeProposal.unwrap {
                val proposedTx = it.stx.tx
                val state = it.stateRef
                require(proposedTx.inputs.contains(state)) { "The proposed state $state is not in the proposed transaction inputs" }
                require(validate(it.modification))
                it
            }
        }
    }
}