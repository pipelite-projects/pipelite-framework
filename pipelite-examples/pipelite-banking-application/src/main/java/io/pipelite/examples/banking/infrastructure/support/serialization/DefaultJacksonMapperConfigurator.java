package io.pipelite.examples.banking.infrastructure.support.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pipelite.spi.channel.convert.json.ObjectMapperConfigurator;
import io.pipelite.examples.banking.domain.Account;
import io.pipelite.examples.banking.domain.AccountId;
import io.pipelite.examples.banking.domain.Balance;
import io.pipelite.examples.banking.infrastructure.message.TransactionMessage;

public class DefaultJacksonMapperConfigurator implements ObjectMapperConfigurator {

    public void configure(ObjectMapper jacksonMapper){

        //jacksonMapper.configure(DeserializationFeature.)
        jacksonMapper.addMixIn(TransactionMessage.class, TransactionMessageMixin.class);
        jacksonMapper.addMixIn(AccountId.class, AccountIdMixin.class);
        jacksonMapper.addMixIn(Account.class, AccountMixin.class);
        jacksonMapper.addMixIn(Balance.class, BalanceMixin.class);

    }
}
