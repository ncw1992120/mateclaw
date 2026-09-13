package vip.mate.dataagent.dataset.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import java.net.URI;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import vip.mate.dataagent.dataset.*;
import vip.mate.dataagent.model.DatasetEntity;
import vip.mate.dataagent.repository.DatasetMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import org.springframework.http.HttpStatus;

class HttpApiDatasetAdapterTest {
    @Test
    void readsDeclaredParametersAndResultPathWithoutHeaders() {
        DatasetMapper mapper = mock(DatasetMapper.class);
        DatasetEntity dataset = dataset();
        when(mapper.selectById(7L)).thenReturn(dataset);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://example.com/v1/orders?status=PAID&page=1&size=10"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("{\"data\":[{\"id\":1,\"status\":\"PAID\"}]}", org.springframework.http.MediaType.APPLICATION_JSON));
        HttpApiDatasetAdapter adapter = new HttpApiDatasetAdapter(mapper, new HttpApiRequestPolicy(), new ObjectMapper(), restTemplate);

        DatasetBatch batch = adapter.read(new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "orders", List.of(),
                        List.of(new DatasetFilter("status", "dimension", "eq", "PAID")), 10, 0, Map.of()));
        assertEquals(1, batch.rows().size());
        assertEquals(1, batch.rows().getFirst().get("id"));
        assertEquals(1, batch.pushdownReport().pushedFilters().size());
        server.verify();
    }

    @Test
    void refusesUndeclaredParameterBeforeSendingRequest() {
        DatasetMapper mapper = mock(DatasetMapper.class);
        when(mapper.selectById(7L)).thenReturn(dataset());
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        HttpApiDatasetAdapter adapter = new HttpApiDatasetAdapter(mapper, new HttpApiRequestPolicy(), new ObjectMapper(), restTemplate);
        DatasetReadException error = assertThrows(DatasetReadException.class, () -> adapter.read(
                new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "orders", List.of(), List.of(new DatasetFilter("secret", "dimension", "eq", "x")), 10, 0, Map.of())));
        assertEquals(DatasetReadErrorCode.INVALID_REQUEST, error.code());
        server.verify();
    }

    @Test
    void mapsOffsetPaginationUsingDeclaredParameterNames() {
        DatasetMapper mapper = mock(DatasetMapper.class);
        DatasetEntity dataset = datasetWithPagination("offset", "offset", "limit");
        when(mapper.selectById(7L)).thenReturn(dataset);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://example.com/v1/orders?offset=20&limit=5"))
                .andRespond(withSuccess("{\"data\":[{\"id\":3}]}", org.springframework.http.MediaType.APPLICATION_JSON));
        HttpApiDatasetAdapter adapter = new HttpApiDatasetAdapter(mapper, new HttpApiRequestPolicy(), new ObjectMapper(), restTemplate);

        DatasetBatch batch = adapter.read(new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "orders", List.of(), List.of(), 5, 20, Map.of()));

        assertEquals(1, batch.rows().size());
        server.verify();
    }

    @Test
    void derivesPageFromOffsetAndRejectsExcessivePageNumber() {
        DatasetMapper mapper = mock(DatasetMapper.class);
        when(mapper.selectById(7L)).thenReturn(datasetWithPagination("page", "page", "size"));
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://example.com/v1/orders?page=5&size=5"))
                .andRespond(withSuccess("{\"data\":[]}", org.springframework.http.MediaType.APPLICATION_JSON));
        HttpApiDatasetAdapter adapter = new HttpApiDatasetAdapter(mapper, new HttpApiRequestPolicy(), new ObjectMapper(), restTemplate);
        adapter.read(new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "orders", List.of(), List.of(), 5, 20, Map.of()));
        server.verify();

        DatasetReadException error = assertThrows(DatasetReadException.class, () -> adapter.read(
                new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "orders", List.of(), List.of(), 10, 10_000, Map.of())));
        assertEquals(DatasetReadErrorCode.RESULT_LIMIT_EXCEEDED, error.code());
    }

    @Test
    void rejectsNonPageAlignedOffsetInsteadOfReturningWrongPage() {
        DatasetMapper mapper = mock(DatasetMapper.class);
        when(mapper.selectById(7L)).thenReturn(datasetWithPagination("page", "page", "size"));
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        HttpApiDatasetAdapter adapter = new HttpApiDatasetAdapter(mapper, new HttpApiRequestPolicy(), new ObjectMapper(), restTemplate);

        DatasetReadException error = assertThrows(DatasetReadException.class, () -> adapter.read(
                new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "orders", List.of(), List.of(), 5, 3, Map.of())));

        assertEquals(DatasetReadErrorCode.INVALID_REQUEST, error.code());
        server.verify();
    }

    @Test
    void classifiesServerErrorsAsSourceUnavailable() {
        DatasetMapper mapper = mock(DatasetMapper.class);
        when(mapper.selectById(7L)).thenReturn(dataset());
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://example.com/v1/orders?page=1&size=10"))
                .andRespond(withServerError());
        HttpApiDatasetAdapter adapter = new HttpApiDatasetAdapter(mapper, new HttpApiRequestPolicy(), new ObjectMapper(), restTemplate);

        DatasetReadException error = assertThrows(DatasetReadException.class, () -> adapter.read(
                new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "orders", List.of(), List.of(), 10, 0, Map.of())));

        assertEquals(DatasetReadErrorCode.SOURCE_UNAVAILABLE, error.code());
        server.verify();
    }

    @Test
    void rejectsMissingResultPathAsInvalidResponse() {
        DatasetMapper mapper = mock(DatasetMapper.class);
        DatasetEntity dataset = dataset();
        dataset.setSourceConfig(dataset.getSourceConfig().replace("$.data", "$.missing"));
        when(mapper.selectById(7L)).thenReturn(dataset);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://example.com/v1/orders?page=1&size=10"))
                .andRespond(withSuccess("{\"data\":[]}", org.springframework.http.MediaType.APPLICATION_JSON));
        HttpApiDatasetAdapter adapter = new HttpApiDatasetAdapter(mapper, new HttpApiRequestPolicy(), new ObjectMapper(), restTemplate);

        DatasetReadException error = assertThrows(DatasetReadException.class, () -> adapter.read(
                new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "orders", List.of(), List.of(), 10, 0, Map.of())));

        assertEquals(DatasetReadErrorCode.INVALID_REQUEST, error.code());
        server.verify();
    }

    @Test
    void mapsCursorFromExecutionParameters() {
        DatasetMapper mapper = mock(DatasetMapper.class);
        DatasetEntity dataset = datasetWithPagination("cursor", "cursor", "limit");
        when(mapper.selectById(7L)).thenReturn(dataset);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://example.com/v1/orders?cursor=next-1&limit=5"))
                .andRespond(withSuccess("{\"data\":[{\"id\":3}]}", org.springframework.http.MediaType.APPLICATION_JSON));
        HttpApiDatasetAdapter adapter = new HttpApiDatasetAdapter(mapper, new HttpApiRequestPolicy(), new ObjectMapper(), restTemplate);

        DatasetBatch batch = adapter.read(new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "orders", List.of(), List.of(), 5, 0, Map.of("cursor", "next-1")));

        assertEquals(1, batch.rows().size());
        server.verify();
    }

    @Test
    void retriesIdempotentRequestOnceAfterServerError() {
        DatasetMapper mapper = mock(DatasetMapper.class);
        DatasetEntity dataset = dataset();
        dataset.setSourceConfig(dataset.getSourceConfig().replace("\"resultPath\":\"$.data\"", "\"resultPath\":\"$.data\",\"idempotent\":true"));
        when(mapper.selectById(7L)).thenReturn(dataset);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://example.com/v1/orders?page=1&size=10")).andRespond(withServerError());
        server.expect(requestTo("https://example.com/v1/orders?page=1&size=10"))
                .andRespond(withSuccess("{\"data\":[{\"id\":1}]}", org.springframework.http.MediaType.APPLICATION_JSON));
        HttpApiDatasetAdapter adapter = new HttpApiDatasetAdapter(mapper, new HttpApiRequestPolicy(), new ObjectMapper(), restTemplate);

        DatasetBatch batch = adapter.read(new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "orders", List.of(), List.of(), 10, 0, Map.of()));

        assertEquals(1, batch.rows().size());
        server.verify();
    }

    @Test
    void rejectsResponseWithTooManyRows() {
        DatasetMapper mapper = mock(DatasetMapper.class);
        when(mapper.selectById(7L)).thenReturn(dataset());
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        String rows = java.util.stream.IntStream.range(0, 10_001)
                .mapToObj(i -> "{\"id\":" + i + "}").collect(java.util.stream.Collectors.joining(","));
        server.expect(requestTo("https://example.com/v1/orders?page=1&size=10"))
                .andRespond(withSuccess("{\"data\":[" + rows + "]}", org.springframework.http.MediaType.APPLICATION_JSON));
        HttpApiDatasetAdapter adapter = new HttpApiDatasetAdapter(mapper, new HttpApiRequestPolicy(), new ObjectMapper(), restTemplate);

        DatasetReadException error = assertThrows(DatasetReadException.class, () -> adapter.read(
                new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "orders", List.of(), List.of(), 10, 0, Map.of())));

        assertEquals(DatasetReadErrorCode.RESULT_LIMIT_EXCEEDED, error.code());
        server.verify();
    }

    @Test
    void classifiesRateLimitAsSourceUnavailable() {
        DatasetMapper mapper = mock(DatasetMapper.class);
        when(mapper.selectById(7L)).thenReturn(dataset());
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://example.com/v1/orders?page=1&size=10"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        HttpApiDatasetAdapter adapter = new HttpApiDatasetAdapter(mapper, new HttpApiRequestPolicy(), new ObjectMapper(), restTemplate);

        DatasetReadException error = assertThrows(DatasetReadException.class, () -> adapter.read(
                new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "orders", List.of(), List.of(), 10, 0, Map.of())));

        assertEquals(DatasetReadErrorCode.SOURCE_UNAVAILABLE, error.code());
        server.verify();
    }

    @Test
    void rejectsResponseExceedingByteLimitBeforeParsing() {
        DatasetMapper mapper = mock(DatasetMapper.class);
        when(mapper.selectById(7L)).thenReturn(dataset());
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        String body = "{\"data\":\"" + "x".repeat(10 * 1024 * 1024 + 1) + "\"}";
        server.expect(requestTo("https://example.com/v1/orders?page=1&size=10"))
                .andRespond(withSuccess(body, org.springframework.http.MediaType.APPLICATION_JSON));
        HttpApiDatasetAdapter adapter = new HttpApiDatasetAdapter(mapper, new HttpApiRequestPolicy(), new ObjectMapper(), restTemplate);

        DatasetReadException error = assertThrows(DatasetReadException.class, () -> adapter.read(
                new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "orders", List.of(), List.of(), 10, 0, Map.of())));

        assertEquals(DatasetReadErrorCode.RESULT_LIMIT_EXCEEDED, error.code());
        server.verify();
    }

    @Test
    void rejectsRedirectResponseInsteadOfParsingOrFollowingIt() {
        DatasetMapper mapper = mock(DatasetMapper.class);
        when(mapper.selectById(7L)).thenReturn(dataset());
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(any(URI.class), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create("https://evil.example/secret")).build());
        HttpApiDatasetAdapter adapter = new HttpApiDatasetAdapter(mapper, new HttpApiRequestPolicy(), new ObjectMapper(), restTemplate);

        DatasetReadException error = assertThrows(DatasetReadException.class, () -> adapter.read(
                new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "orders", List.of(), List.of(), 10, 0, Map.of())));

        assertEquals(DatasetReadErrorCode.SOURCE_UNAVAILABLE, error.code());
        verify(restTemplate).exchange(any(URI.class), any(), any(), eq(String.class));
    }

    @Test
    void revalidatesDnsImmediatelyBeforeNetworkAttempt() {
        DatasetMapper mapper = mock(DatasetMapper.class);
        when(mapper.selectById(7L)).thenReturn(dataset());
        HttpApiRequestPolicy policy = mock(HttpApiRequestPolicy.class);
        when(policy.mapFilters(any(), any())).thenReturn(Map.of());
        doNothing().doThrow(new IllegalArgumentException("endpoint resolves to private or metadata address"))
                .when(policy).validate(any(URI.class), any());
        RestTemplate restTemplate = mock(RestTemplate.class);
        HttpApiDatasetAdapter adapter = new HttpApiDatasetAdapter(mapper, policy, new ObjectMapper(), restTemplate);

        DatasetReadException error = assertThrows(DatasetReadException.class, () -> adapter.read(
                new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "orders", List.of(), List.of(), 10, 0, Map.of())));

        assertEquals(DatasetReadErrorCode.INVALID_REQUEST, error.code());
        verifyNoInteractions(restTemplate);
        verify(policy, times(2)).validate(any(URI.class), any());
    }

    private DatasetEntity dataset() {
        DatasetEntity d = new DatasetEntity(); d.setId(7L); d.setName("orders"); d.setSourceType(DatasetSourceType.HTTP_API.name());
        d.setSourceConfig("{\"endpoint\":\"https://example.com/v1/orders\",\"method\":\"GET\",\"allowedHosts\":[\"example.com\"],\"allowedQueryParams\":[\"status\"],\"paginationMode\":\"page\",\"pageParam\":\"page\",\"sizeParam\":\"size\",\"resultPath\":\"$.data\"}");
        return d;
    }

    private DatasetEntity datasetWithPagination(String mode, String pageParam, String sizeParam) {
        DatasetEntity d = dataset();
        d.setSourceConfig("{\"endpoint\":\"https://example.com/v1/orders\",\"method\":\"GET\",\"allowedHosts\":[\"example.com\"],\"allowedQueryParams\":[],\"paginationMode\":\"" + mode + "\",\"pageParam\":\"" + pageParam + "\",\"sizeParam\":\"" + sizeParam + "\",\"resultPath\":\"$.data\"}");
        return d;
    }
}
