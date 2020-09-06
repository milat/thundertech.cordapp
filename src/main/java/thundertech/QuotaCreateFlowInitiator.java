package thundertech;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.security.PublicKey;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;

import static com.google.common.collect.Iterables.find;

// `TokenIssueFlowInitiator` means that we can start the flow directly (instead of
// solely in response to another flow).
@InitiatingFlow
// `StartableByRPC` means that a node operator can start the flow via RPC.
@StartableByRPC
// Like all states, implements `FlowLogic`.
public class QuotaCreateFlowInitiator extends FlowLogic<Void> {
    private final Integer amount;

    // Flows can take constructor arguments to parameterize the execution of the flow.
    public QuotaCreateFlowInitiator(Integer amount) {
        this.amount = amount;
    }

    private final ProgressTracker progressTracker = new ProgressTracker();

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    // Must be marked `@Suspendable` to allow the flow to be suspended
    // mid-execution.
    @Suspendable
    @Override
    // Overrides `call`, where we define the logic executed by the flow.
    public Void call() throws FlowException {
        Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

        TransactionBuilder txBuilder = new TransactionBuilder();
        txBuilder.setNotary(notary);

        Party party = getOurIdentity();

        QuotaState outputQuotaState = new QuotaState(
                party,
                party,
                amount);
        txBuilder.addOutputState(outputQuotaState, QuotaContract.ID);

        QuotaContract.Commands.Issue commandData = new QuotaContract.Commands.Issue();
        List<PublicKey> requiredSigners = ImmutableList.of(
                party.getOwningKey());
        txBuilder.addCommand(commandData, requiredSigners);

        txBuilder.verify(getServiceHub());

        SignedTransaction fullySignedTx = getServiceHub().signInitialTransaction(txBuilder);

        subFlow(new FinalityFlow(fullySignedTx, new ArrayList()));

        return null;
    }
}