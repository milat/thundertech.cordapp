package thundertech;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import org.jetbrains.annotations.NotNull;

import java.util.List;

// Like all states, implements `ContractState`.
@BelongsToContract(QuotaContract.class)
public class QuotaState implements ContractState {
    // The attributes that will be stored on the ledger as part of the state.
    private final Party mill;
    private final Party owner;
    private final Integer amount;

    // The constructor used to create an instance of the state.
    public QuotaState(Party mill, Party owner, Integer amount) {
        this.mill = mill;
        this.owner = owner;
        this.amount = amount;
    }

    // Overrides `participants`, the only field defined by `ContractState`.
    // Defines which parties will store the state.
    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return ImmutableList.of(mill, owner);
    }

    // Getters for the state's attributes.
    public Party getMill() {
        return mill;
    }

    public Party getOwner() {
        return owner;
    }

    public Integer getAmount() {
        return amount;
    }
}