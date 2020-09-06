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
import java.util.stream.Collectors;

import static com.google.common.collect.Iterables.find;

// `TokenIssueFlowInitiator` means that we can start the flow directly (instead of
// solely in response to another flow).
@InitiatingFlow
// `StartableByRPC` means that a node operator can start the flow via RPC.
@StartableByRPC
// Like all states, implements `FlowLogic`.
public class QuotaTransferFlowInitiator extends FlowLogic<Void> {
    private final Party mill;
    private final Integer amount;
    private final Party newOwner;

    // Flows can take constructor arguments to parameterize the execution of the flow.
    public QuotaTransferFlowInitiator(Party mill, Integer amount, Party newOwner) {
        this.mill = mill;
        this.amount = amount;
        this.newOwner = newOwner;
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

        AtomicInteger totalQuotaAvailable = new AtomicInteger();
        List<StateAndRef<QuotaState>> inputStateAndRef = new ArrayList<>();
        AtomicInteger change = new AtomicInteger(0);

        // We extract all the `QuotaState`s from the vault.
        List<StateAndRef<QuotaState>> quotaStateAndRefs = getServiceHub().getVaultService().queryBy(QuotaState.class).getStates();

        // We find the `QuotaState` with the correct mill.
        List<StateAndRef<QuotaState>> inputQuotaStateAndRef = quotaStateAndRefs.stream().filter(quotaStateAndRef -> {
            if (
                    quotaStateAndRef.getState().getData().getMill().equals(mill) &&
                    quotaStateAndRef.getState().getData().getOwner().equals(getOurIdentity())
            ) {

                if (totalQuotaAvailable.get() < amount)
                    inputStateAndRef.add(quotaStateAndRef);

                totalQuotaAvailable.set(totalQuotaAvailable.get() + quotaStateAndRef.getState().getData().getAmount());

                // Determine the change needed to be returned
                if(change.get() == 0 && totalQuotaAvailable.get() > amount){
                    change.set(totalQuotaAvailable.get() - amount);
                }
                return true;
            }
            return false;
        }).collect(Collectors.toList());

        if (totalQuotaAvailable.get() < amount)
            throw new FlowException("Insufficient balance");

        // We use the notary used by the input state.
        Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

        // We build a transaction using a `TransactionBuilder`.
        TransactionBuilder txBuilder = new TransactionBuilder();

        // After creating the `TransactionBuilder`, we must specify which
        // notary it will use.
        txBuilder.setNotary(notary);

        // We add the output QuotaState to the transaction. Note that we also
        // specify which contract class to use for verification.
        QuotaState outputQuotaState = new QuotaState(mill, newOwner, amount);
        txBuilder.addOutputState(outputQuotaState, QuotaContract.ID);

        // We add the input QuotaState to the transaction.
        inputStateAndRef.forEach(txBuilder::addInputState);

        if(change.get() > 0){
            QuotaState changeState = new QuotaState(
                    mill,
                    getOurIdentity(),
                    change.get()
            );
            txBuilder.addOutputState(changeState, QuotaContract.ID);
        }

        // We add the Issue command to the transaction.
        // Note that we also specific who is required to sign the transaction.
        QuotaContract.Commands.Transfer commandData = new QuotaContract.Commands.Transfer();
        List<PublicKey> requiredSigners = ImmutableList.of(
                mill.getOwningKey(), newOwner.getOwningKey());
        txBuilder.addCommand(commandData, requiredSigners);

        // We check that the transaction builder we've created meets the
        // contracts of the input and output states.
        txBuilder.verify(getServiceHub());

        // We finalise the transaction builder by signing it,
        // converting it into a `SignedTransaction`.
        SignedTransaction partlySignedTx = getServiceHub().signInitialTransaction(txBuilder);

        // We use `CollectSignaturesFlow` to automatically gather a
        // signature from each counterparty. The counterparty will need to
        // call `SignTransactionFlow` to decided whether or not to sign.
        FlowSession ownerSession = initiateFlow(newOwner);
        SignedTransaction fullySignedTx = subFlow(
                new CollectSignaturesFlow(partlySignedTx, ImmutableSet.of(ownerSession)));

        // We use `FinalityFlow` to automatically notarise the transaction
        // and have it recorded by all the `participants` of all the
        // transaction's states.
        subFlow(new FinalityFlow(fullySignedTx, ownerSession));

        return null;
    }
}