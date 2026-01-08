package uk.gov.moj.cpp.notification.notify.paas.service;

import static com.microsoft.azure.storage.StorageErrorCode.NONE;
import static com.microsoft.azure.storage.table.TableOperation.merge;
import static java.lang.String.format;
import static java.lang.System.getenv;
import static uk.gov.moj.cpp.notification.notify.paas.EnvironmentHelper.getEnvironmentVariableForSmtpSettings;
import static uk.gov.moj.cpp.notification.notify.paas.GenerateEmailNotificationHelper.getOutlookSettings;

import uk.gov.moj.cpp.notification.notify.paas.entity.NextServer;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.CloudTableClient;
import com.microsoft.azure.storage.table.TableOperation;
import com.microsoft.azure.storage.table.TableQuery;

@SuppressWarnings({"squid:S3655","squid:S2221","squid:S1162"})
public class AzureCloudStorageService {

    private static final String CLOUD_STORAGE_STRING = "storage-connection-string";
    private static final String SMTPSERVERS = "SMTPServer";


    public int getNextSmtpServer(final Logger logger) throws StorageException {
        try {
            final int numberOfO365Servers = getOutlookSettings(getEnvironmentVariableForSmtpSettings()).getOutlookCredentials().size();
            final Comparator<NextServer> getByTimestamp = Comparator.comparing(NextServer::getTimestamp);
            final CloudTable smtpServersTable = getTable(SMTPSERVERS);
            final TableQuery<NextServer> query = TableQuery.from(NextServer.class).where(
                  TableQuery.combineFilters( TableQuery.generateFilterCondition("PartitionKey", TableQuery.QueryComparisons.EQUAL, "next"),
                          TableQuery.Operators.AND,
                          TableQuery.generateFilterCondition("RowKey", TableQuery.QueryComparisons.EQUAL, "server"))
            );
            final Iterable<NextServer> resultList = smtpServersTable.execute(query);
            final NextServer nextServer = StreamSupport.stream(resultList.spliterator(), false).sorted(getByTimestamp).findFirst().get();
            if (logger.isLoggable(Level.INFO)) {
                logger.info(format("Selected server: %s ", nextServer.getServerId()));
            }
            updateHit(nextServer, numberOfO365Servers); //just to get an update on timestamp on Azure table
            return nextServer.getServerId();
        } catch (Exception e) {
            throw new StorageException(NONE.toString(), "Error while retrieving smtp servers from Azure Error:" + e.getMessage(), e);
        }

    }

    private void updateHit(final NextServer serverToUpdate,final int maxServers) throws StorageException, URISyntaxException, InvalidKeyException {
        final int nextServerId = serverToUpdate.getServerId() < maxServers ? serverToUpdate.getServerId() +1 : 1;
        final NextServer updatedEntity = NextServer.NextServerBuilder.aNextServer()
                .withServerId(nextServerId)
                .withRowKey(serverToUpdate.getRowKey())
                .withPartitionKey(serverToUpdate.getPartitionKey())
                .withEtag(serverToUpdate.getEtag())
                .build();
        final CloudTable smtpServersTable = getTable(SMTPSERVERS);
        final TableOperation updateHit = merge(updatedEntity);
        smtpServersTable.execute(updateHit);
    }




    private CloudTable getTable(final String tableName) throws URISyntaxException, StorageException, InvalidKeyException {
        final CloudStorageAccount cloudStorageAccount = getCloudStorageAccount();
        final CloudTableClient cloudTableClient = cloudStorageAccount.createCloudTableClient();
        return cloudTableClient.getTableReference(tableName);
    }

    private CloudStorageAccount getCloudStorageAccount() throws URISyntaxException, InvalidKeyException {
        return CloudStorageAccount.parse(getenv(CLOUD_STORAGE_STRING));
    }
}
