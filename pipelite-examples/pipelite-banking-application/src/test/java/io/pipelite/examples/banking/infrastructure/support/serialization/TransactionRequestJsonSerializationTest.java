package io.pipelite.examples.banking.infrastructure.support.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;

import java.net.URL;

public class TransactionRequestJsonSerializationTest {

    private final DefaultJacksonMapperConfigurator jacksonMapperConfigurator = new DefaultJacksonMapperConfigurator();

    private final URL transactionRequest01ResourceURL = TransactionRequestJsonSerializationTest.class
        .getResource("/io/pipelite/examples/banking/infrastructure/support/serialization/transaction-request-01.json");

    private ObjectMapper jacksonMapper;

    @Before
    public void setup(){
        jacksonMapper = new ObjectMapper();
        jacksonMapperConfigurator.configure(jacksonMapper);
    }

    /*
    @Test
    public void givenTransactionRequest_whenSerialize_thenDoCorrectly() throws IOException, JSONException {

        final TransactionRequest txRequest = new TransactionRequest("eu-central-bank", "439a4f7db567", "485d8a5f-a64b-481f-99d0-1d21b695271b");
        txRequest.setCategory(Transaction.Category.PAYMENT);
        txRequest.setReceiverId("ch-bank-01");
        txRequest.setReceiverAccount("c5251f7e");
        txRequest.setAmount(BigDecimal.valueOf(100.00));
        txRequest.setCurrencyCode("EUR");
        txRequest.setCircuit("ONLINE-PAYMENTS");

        final String serializedTxRequest = jacksonMapper.writeValueAsString(txRequest);

        assert transactionRequest01ResourceURL != null;

        try(final InputStream transactionRequest01ResourceIS = transactionRequest01ResourceURL.openStream()){

            final String expectecTxRequest = new BufferedReader(new InputStreamReader(transactionRequest01ResourceIS))
                .lines().collect(Collectors.joining("\n"));

            JSONAssert.assertEquals(serializedTxRequest, expectecTxRequest, JSONCompareMode.LENIENT);

        }

    }

    @Test
    public void givenTransactionRequest_whenDeserialize_thenDoCorrectly() throws IOException {

        assert transactionRequest01ResourceURL != null;
        final TransactionRequest deserializedTxRequest = jacksonMapper.readValue(transactionRequest01ResourceURL, TransactionRequest.class);

        final TransactionRequest expectedTxRequest = new TransactionRequest("eu-central-bank", "439a4f7db567", "485d8a5f-a64b-481f-99d0-1d21b695271b");
        expectedTxRequest.setCategory(Transaction.Category.PAYMENT);
        expectedTxRequest.setReceiverId("ch-bank-01");
        expectedTxRequest.setReceiverAccount("c5251f7e");
        expectedTxRequest.setAmount(new BigDecimal("100.00"));
        expectedTxRequest.setCurrencyCode("EUR");
        expectedTxRequest.setCircuit("ONLINE-PAYMENTS");

        Assert.assertEquals(expectedTxRequest, deserializedTxRequest);
        Assert.assertEquals(expectedTxRequest.getIssuerId(), deserializedTxRequest.getIssuerId());
        Assert.assertEquals(expectedTxRequest.getIssuerAccount(), deserializedTxRequest.getIssuerAccount());
        Assert.assertEquals(expectedTxRequest.getCategory(), deserializedTxRequest.getCategory());
        Assert.assertEquals(expectedTxRequest.getReceiverId(), deserializedTxRequest.getReceiverId());
        Assert.assertEquals(expectedTxRequest.getReceiverAccount(), deserializedTxRequest.getReceiverAccount());
        Assert.assertEquals(expectedTxRequest.getAmount(), deserializedTxRequest.getAmount());
        Assert.assertEquals(expectedTxRequest.getCurrencyCode(), deserializedTxRequest.getCurrencyCode());
        Assert.assertEquals(expectedTxRequest.getCircuit(), deserializedTxRequest.getCircuit());

    }
    */

}
