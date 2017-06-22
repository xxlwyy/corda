package net.corda.loadtest.tests

import net.corda.client.mock.Generator
import net.corda.core.contracts.Amount
import net.corda.core.contracts.USD
import net.corda.core.failure
import net.corda.core.flows.FlowException
import net.corda.core.getOrThrow
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.success
import net.corda.core.utilities.loggerFor
import net.corda.flows.CashFlowCommand
import net.corda.loadtest.LoadTest

object StabilityTest {
    private val log = loggerFor<StabilityTest>()
    fun crossCashTest(batchSize: Int) = LoadTest<CrossCashCommand, Unit>(
            "Creating Cash transactions",
            generate = { _, _ ->
                val payments = (1..batchSize).flatMap {
                    simpleNodes.flatMap { payer -> simpleNodes.map { payer to it } }
                            .filter { it.first != it.second }
                            .map { (payer, payee) -> CrossCashCommand(CashFlowCommand.PayCash(Amount(1, USD), payee.info.legalIdentity), payer) }
                }
                Generator.pure(payments)
            },
            interpret = { _, _ -> },
            execute = { command ->
                val result = command.command.startFlow(command.node.proxy).returnValue
                result.failure {
                    log.error("Failure[$command]", it)
                }
                result.success {
                    log.info("Success[$command]: $result")
                }
            },
            gatherRemoteState = {}
    )

    fun selfIssueTest(batchSize: Int) = LoadTest<SelfIssueCommand, Unit>(
            "Self issuing lot of cash",
            generate = { _, _ ->
                // Self issue cash is fast, its ok to flood the node with this command.
                val generateIssue = (1..batchSize).flatMap {
                    simpleNodes.map { issuer ->
                        SelfIssueCommand(CashFlowCommand.IssueCash(Amount(100000, USD), OpaqueBytes.of(0), issuer.info.legalIdentity, notary.info.notaryIdentity), issuer)
                    }
                }
                Generator.pure(generateIssue)
            },
            interpret = { _, _ -> },
            execute = { command ->
                try {
                    val result = command.command.startFlow(command.node.proxy).returnValue.getOrThrow()
                    log.info("Success: $result")
                } catch (e: FlowException) {
                    log.error("Failure", e)
                }
            },
            gatherRemoteState = {}
    )
}
