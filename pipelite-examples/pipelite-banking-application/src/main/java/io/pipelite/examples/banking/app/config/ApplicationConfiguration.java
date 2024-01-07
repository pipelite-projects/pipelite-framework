package io.pipelite.examples.banking.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.examples.banking.app.config.channel.DefaultHttpChannelConfigurer;
import io.pipelite.examples.banking.app.config.channel.DefaultKafkaChannelConfigurer;
import io.pipelite.examples.banking.app.core.support.LogHelper;
import io.pipelite.examples.banking.domain.Account;
import io.pipelite.examples.banking.domain.AccountRepository;
import io.pipelite.examples.banking.infrastructure.support.serialization.DefaultJacksonMapperConfigurator;
import io.pipelite.spring.EnablePipelite;
import io.pipelite.spring.context.PipeliteContextInitializer;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Arrays;

@Configuration
@EnablePipelite
@Import(FlowConfiguration.class)
public class ApplicationConfiguration implements PipeliteContextInitializer {

    private final Logger logger = LogHelper.getBankingLogger();

    @Value("${application.bank-id}")
    private String bankId;

    @Value("classpath:/accounts_#{'${application.bank-id}'.toLowerCase().replaceAll('[^a-z0-9_]+', '_')}.json")
    private Resource bankAccountsResource;

    @Autowired
    private AccountRepository accountRepository;

    @Override
    public void initializeContext(PipeliteContext context) {
        context.addChannelConfigurer(new DefaultHttpChannelConfigurer());
        context.addChannelConfigurer(new DefaultKafkaChannelConfigurer(bankId));
    }

    @EventListener
    public void initializeAccountRepository(ContextRefreshedEvent evt) throws IOException {

        final ObjectMapper jacksonMapper = new ObjectMapper();
        DefaultJacksonMapperConfigurator jacksonMapperConfigurator = new DefaultJacksonMapperConfigurator();
        jacksonMapperConfigurator.configure(jacksonMapper);
        final ObjectReader reader = jacksonMapper.readerFor(Account[].class);
        final Account[] accounts = reader.readValue(bankAccountsResource.getURL());
        Arrays.asList(accounts).forEach(account -> {
            accountRepository.save(account);
            if(logger.isInfoEnabled()){
                logger.info("Account loaded successfully [accountId: '{}', accountBalance: {}]", account.getId(), account.getBalance());
            }
        });

    }
}
