package thundertech;

import net.corda.core.contracts.*;
import net.corda.core.transactions.LedgerTransaction;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;

import java.security.PublicKey;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;

// Like all contracts, implements `Contract`.
public class QuotaContract implements Contract {
    // Used to reference the contract in transactions.
    public static final String ID = "thundertech.QuotaContract";

    public interface Commands extends CommandData {
        class Issue implements Commands { }
        class Transfer implements Commands { }
        class Exit implements Commands { }
    }

    @Override
    public void verify(LedgerTransaction tx) throws IllegalArgumentException {
        CommandWithParties<Commands> command = requireSingleCommand(tx.getCommands(), Commands.class);

        if (command.getValue() instanceof Commands.Issue) {

            final QuotaState quotaStateOutput = tx.outputsOfType(QuotaState.class).get(0);

            if (tx.getInputStates().size() != 0)
                throw new IllegalArgumentException("Zero Inputs Expected");

            if (tx.getOutputStates().size() != 1)
                throw new IllegalArgumentException("One Output Expected");

            if (tx.outputsOfType(QuotaState.class).size() != 1)
                throw new IllegalArgumentException("Quota transfer output should be an QuotaState.");

            if (quotaStateOutput.getAmount() < 1)
                throw new IllegalArgumentException("Positive amount expected");

            if (!(tx.getCommand(0).getSigners().contains(quotaStateOutput.getMill().getOwningKey())))
                throw new IllegalArgumentException("Mill must sign");

        } else if (command.getValue() instanceof Commands.Transfer) {
            // Checking the shape of the transaction.
            if (!(tx.getOutputStates().size() == 1 || tx.getOutputStates().size() == 2))
                throw new IllegalArgumentException("Quota transfer should have one or two outputs.");

            if(tx.getInputs().size() < 1)
                throw new IllegalArgumentException("At least 1 input expected");

            // Grabbing the transaction's contents.
            final QuotaState quotaStateInput = tx.inputsOfType(QuotaState.class).get(0);
            final QuotaState quotaStateOutput = tx.outputsOfType(QuotaState.class).get(0);

            // Checking the transaction's required signers.
            final List<PublicKey> requiredSigners = command.getSigners();

            // Input amount must be equal to output amount
            AtomicInteger inputSum = new AtomicInteger();
            tx.getInputs().forEach(contractStateStateAndRef -> {
                QuotaState inputState = (QuotaState)contractStateStateAndRef.getState().getData();
                inputSum.set(inputSum.get() + inputState.getAmount());
            });

            AtomicInteger outputSum = new AtomicInteger();
            tx.getInputs().forEach(contractStateStateAndRef -> {
                QuotaState outputState = (QuotaState)contractStateStateAndRef.getState().getData();
                outputSum.set(outputSum.get() + outputState.getAmount());
            });

            if (inputSum.get() != outputSum.get())
                throw new IllegalArgumentException("Incorrect Spending");

            if (inputSum.get() < 1)
                throw new IllegalArgumentException("Positive amount expected");

        } else if (command.getValue() instanceof Commands.Exit) {
            // Exit transaction rules...

        } else throw new IllegalArgumentException("Unrecognised command.");
    }
}