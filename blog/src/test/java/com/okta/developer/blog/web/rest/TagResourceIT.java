package com.okta.developer.blog.web.rest;

import static com.okta.developer.blog.domain.TagAsserts.*;
import static com.okta.developer.blog.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.okta.developer.blog.IntegrationTest;
import com.okta.developer.blog.domain.Tag;
import com.okta.developer.blog.repository.EntityManager;
import com.okta.developer.blog.repository.TagRepository;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for the {@link TagResource} REST controller.
 */
@IntegrationTest
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_ENTITY_TIMEOUT)
@WithMockUser
class TagResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/tags";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private WebTestClient webTestClient;

    private Tag tag;

    private Tag insertedTag;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Tag createEntity() {
        return new Tag().name(DEFAULT_NAME);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Tag createUpdatedEntity() {
        return new Tag().name(UPDATED_NAME);
    }

    public static void deleteEntities(EntityManager em) {
        try {
            em.deleteAll(Tag.class).block();
        } catch (Exception e) {
            // It can fail, if other entities are still referring this - it will be removed later.
        }
    }

    @BeforeEach
    public void setupCsrf() {
        webTestClient = webTestClient.mutateWith(csrf());
    }

    @BeforeEach
    public void initTest() {
        tag = createEntity();
    }

    @AfterEach
    public void cleanup() {
        if (insertedTag != null) {
            tagRepository.delete(insertedTag).block();
            insertedTag = null;
        }
        deleteEntities(em);
    }

    @Test
    void createTag() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Tag
        var returnedTag = webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tag))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(Tag.class)
            .returnResult()
            .getResponseBody();

        // Validate the Tag in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertTagUpdatableFieldsEquals(returnedTag, getPersistedTag(returnedTag));

        insertedTag = returnedTag;
    }

    @Test
    void createTagWithExistingId() throws Exception {
        // Create the Tag with an existing ID
        tag.setId(1L);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tag))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        tag.setName(null);

        // Create the Tag, which fails.

        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tag))
            .exchange()
            .expectStatus()
            .isBadRequest();

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllTags() {
        // Initialize the database
        insertedTag = tagRepository.save(tag).block();

        // Get all the tagList
        webTestClient
            .get()
            .uri(ENTITY_API_URL + "?sort=id,desc")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.[*].id")
            .value(hasItem(tag.getId().intValue()))
            .jsonPath("$.[*].name")
            .value(hasItem(DEFAULT_NAME));
    }

    @Test
    void getTag() {
        // Initialize the database
        insertedTag = tagRepository.save(tag).block();

        // Get the tag
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, tag.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id")
            .value(is(tag.getId().intValue()))
            .jsonPath("$.name")
            .value(is(DEFAULT_NAME));
    }

    @Test
    void getNonExistingTag() {
        // Get the tag
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, Long.MAX_VALUE)
            .accept(MediaType.APPLICATION_PROBLEM_JSON)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void putExistingTag() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.save(tag).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the tag
        Tag updatedTag = tagRepository.findById(tag.getId()).block();
        updatedTag.name(UPDATED_NAME);

        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, updatedTag.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(updatedTag))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedTagToMatchAllProperties(updatedTag);
    }

    @Test
    void putNonExistingTag() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        tag.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, tag.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tag))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchTag() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        tag.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tag))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamTag() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        tag.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tag))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateTagWithPatch() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.save(tag).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the tag using partial update
        Tag partialUpdatedTag = new Tag();
        partialUpdatedTag.setId(tag.getId());

        partialUpdatedTag.name(UPDATED_NAME);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedTag.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedTag))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Tag in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertTagUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedTag, tag), getPersistedTag(tag));
    }

    @Test
    void fullUpdateTagWithPatch() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.save(tag).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the tag using partial update
        Tag partialUpdatedTag = new Tag();
        partialUpdatedTag.setId(tag.getId());

        partialUpdatedTag.name(UPDATED_NAME);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedTag.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedTag))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Tag in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertTagUpdatableFieldsEquals(partialUpdatedTag, getPersistedTag(partialUpdatedTag));
    }

    @Test
    void patchNonExistingTag() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        tag.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, tag.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(tag))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchTag() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        tag.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(tag))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamTag() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        tag.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(tag))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteTag() {
        // Initialize the database
        insertedTag = tagRepository.save(tag).block();

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the tag
        webTestClient
            .delete()
            .uri(ENTITY_API_URL_ID, tag.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNoContent();

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return tagRepository.count().block();
    }

    protected void assertIncrementedRepositoryCount(long countBefore) {
        assertThat(countBefore + 1).isEqualTo(getRepositoryCount());
    }

    protected void assertDecrementedRepositoryCount(long countBefore) {
        assertThat(countBefore - 1).isEqualTo(getRepositoryCount());
    }

    protected void assertSameRepositoryCount(long countBefore) {
        assertThat(countBefore).isEqualTo(getRepositoryCount());
    }

    protected Tag getPersistedTag(Tag tag) {
        return tagRepository.findById(tag.getId()).block();
    }

    protected void assertPersistedTagToMatchAllProperties(Tag expectedTag) {
        // Test fails because reactive api returns an empty object instead of null
        // assertTagAllPropertiesEquals(expectedTag, getPersistedTag(expectedTag));
        assertTagUpdatableFieldsEquals(expectedTag, getPersistedTag(expectedTag));
    }

    protected void assertPersistedTagToMatchUpdatableProperties(Tag expectedTag) {
        // Test fails because reactive api returns an empty object instead of null
        // assertTagAllUpdatablePropertiesEquals(expectedTag, getPersistedTag(expectedTag));
        assertTagUpdatableFieldsEquals(expectedTag, getPersistedTag(expectedTag));
    }
}
