package thundertech;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.*;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;
import org.jetbrains.annotations.NotNull;

// `InitiatedBy` means that we will start this flow in response to a
// message from `ArtTransferFlow.Initiator`.
@InitiatedBy(QuotaTransferFlowInitiator.class)
public class QuotaTransferFlowResponder extends FlowLogic<Void> {
    private final FlowSession counterpartySession;

    // Responder flows always have a single constructor argument - a
    // `FlowSession` with the counterparty who initiated the flow.
    public QuotaTransferFlowResponder(FlowSession counterpartySession) {
        this.counterpartySession = counterpartySession;
    }

    private final ProgressTracker progressTracker = new ProgressTracker();

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    @Suspendable
    @Override
    public Void call() throws FlowException {
        SignedTransaction signedTransaction = subFlow(new SignTransactionFlow(this.counterpartySession) {
                                                          @Override
                                                          @Suspendable
                                                          protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {

                                                          }
                                                      }

        );

        subFlow(new ReceiveFinalityFlow(counterpartySession, signedTransaction.getId()));

        return null;
    }
}