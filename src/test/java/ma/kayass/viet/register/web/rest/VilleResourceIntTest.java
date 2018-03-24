package ma.kayass.viet.register.web.rest;

import ma.kayass.viet.register.VietRegisterApp;

import ma.kayass.viet.register.domain.Ville;
import ma.kayass.viet.register.repository.VilleRepository;
import ma.kayass.viet.register.service.VilleService;
import ma.kayass.viet.register.repository.search.VilleSearchRepository;
import ma.kayass.viet.register.service.dto.VilleDTO;
import ma.kayass.viet.register.service.mapper.VilleMapper;
import ma.kayass.viet.register.web.rest.errors.ExceptionTranslator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.List;

import static ma.kayass.viet.register.web.rest.TestUtil.createFormattingConversionService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test class for the VilleResource REST controller.
 *
 * @see VilleResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = VietRegisterApp.class)
public class VilleResourceIntTest {

    private static final String DEFAULT_NOM = "AAAAAAAAAA";
    private static final String UPDATED_NOM = "BBBBBBBBBB";

    @Autowired
    private VilleRepository villeRepository;

    @Autowired
    private VilleMapper villeMapper;

    @Autowired
    private VilleService villeService;

    @Autowired
    private VilleSearchRepository villeSearchRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restVilleMockMvc;

    private Ville ville;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final VilleResource villeResource = new VilleResource(villeService);
        this.restVilleMockMvc = MockMvcBuilders.standaloneSetup(villeResource)
            .setCustomArgumentResolvers(pageableArgumentResolver)
            .setControllerAdvice(exceptionTranslator)
            .setConversionService(createFormattingConversionService())
            .setMessageConverters(jacksonMessageConverter).build();
    }

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Ville createEntity(EntityManager em) {
        Ville ville = new Ville()
            .nom(DEFAULT_NOM);
        return ville;
    }

    @Before
    public void initTest() {
        villeSearchRepository.deleteAll();
        ville = createEntity(em);
    }

    @Test
    @Transactional
    public void createVille() throws Exception {
        int databaseSizeBeforeCreate = villeRepository.findAll().size();

        // Create the Ville
        VilleDTO villeDTO = villeMapper.toDto(ville);
        restVilleMockMvc.perform(post("/api/villes")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(villeDTO)))
            .andExpect(status().isCreated());

        // Validate the Ville in the database
        List<Ville> villeList = villeRepository.findAll();
        assertThat(villeList).hasSize(databaseSizeBeforeCreate + 1);
        Ville testVille = villeList.get(villeList.size() - 1);
        assertThat(testVille.getNom()).isEqualTo(DEFAULT_NOM);

        // Validate the Ville in Elasticsearch
        Ville villeEs = villeSearchRepository.findOne(testVille.getId());
        assertThat(villeEs).isEqualToIgnoringGivenFields(testVille);
    }

    @Test
    @Transactional
    public void createVilleWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = villeRepository.findAll().size();

        // Create the Ville with an existing ID
        ville.setId(1L);
        VilleDTO villeDTO = villeMapper.toDto(ville);

        // An entity with an existing ID cannot be created, so this API call must fail
        restVilleMockMvc.perform(post("/api/villes")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(villeDTO)))
            .andExpect(status().isBadRequest());

        // Validate the Ville in the database
        List<Ville> villeList = villeRepository.findAll();
        assertThat(villeList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllVilles() throws Exception {
        // Initialize the database
        villeRepository.saveAndFlush(ville);

        // Get all the villeList
        restVilleMockMvc.perform(get("/api/villes?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(ville.getId().intValue())))
            .andExpect(jsonPath("$.[*].nom").value(hasItem(DEFAULT_NOM.toString())));
    }

    @Test
    @Transactional
    public void getVille() throws Exception {
        // Initialize the database
        villeRepository.saveAndFlush(ville);

        // Get the ville
        restVilleMockMvc.perform(get("/api/villes/{id}", ville.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(ville.getId().intValue()))
            .andExpect(jsonPath("$.nom").value(DEFAULT_NOM.toString()));
    }

    @Test
    @Transactional
    public void getNonExistingVille() throws Exception {
        // Get the ville
        restVilleMockMvc.perform(get("/api/villes/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateVille() throws Exception {
        // Initialize the database
        villeRepository.saveAndFlush(ville);
        villeSearchRepository.save(ville);
        int databaseSizeBeforeUpdate = villeRepository.findAll().size();

        // Update the ville
        Ville updatedVille = villeRepository.findOne(ville.getId());
        // Disconnect from session so that the updates on updatedVille are not directly saved in db
        em.detach(updatedVille);
        updatedVille
            .nom(UPDATED_NOM);
        VilleDTO villeDTO = villeMapper.toDto(updatedVille);

        restVilleMockMvc.perform(put("/api/villes")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(villeDTO)))
            .andExpect(status().isOk());

        // Validate the Ville in the database
        List<Ville> villeList = villeRepository.findAll();
        assertThat(villeList).hasSize(databaseSizeBeforeUpdate);
        Ville testVille = villeList.get(villeList.size() - 1);
        assertThat(testVille.getNom()).isEqualTo(UPDATED_NOM);

        // Validate the Ville in Elasticsearch
        Ville villeEs = villeSearchRepository.findOne(testVille.getId());
        assertThat(villeEs).isEqualToIgnoringGivenFields(testVille);
    }

    @Test
    @Transactional
    public void updateNonExistingVille() throws Exception {
        int databaseSizeBeforeUpdate = villeRepository.findAll().size();

        // Create the Ville
        VilleDTO villeDTO = villeMapper.toDto(ville);

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restVilleMockMvc.perform(put("/api/villes")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(villeDTO)))
            .andExpect(status().isCreated());

        // Validate the Ville in the database
        List<Ville> villeList = villeRepository.findAll();
        assertThat(villeList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteVille() throws Exception {
        // Initialize the database
        villeRepository.saveAndFlush(ville);
        villeSearchRepository.save(ville);
        int databaseSizeBeforeDelete = villeRepository.findAll().size();

        // Get the ville
        restVilleMockMvc.perform(delete("/api/villes/{id}", ville.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate Elasticsearch is empty
        boolean villeExistsInEs = villeSearchRepository.exists(ville.getId());
        assertThat(villeExistsInEs).isFalse();

        // Validate the database is empty
        List<Ville> villeList = villeRepository.findAll();
        assertThat(villeList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void searchVille() throws Exception {
        // Initialize the database
        villeRepository.saveAndFlush(ville);
        villeSearchRepository.save(ville);

        // Search the ville
        restVilleMockMvc.perform(get("/api/_search/villes?query=id:" + ville.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(ville.getId().intValue())))
            .andExpect(jsonPath("$.[*].nom").value(hasItem(DEFAULT_NOM.toString())));
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Ville.class);
        Ville ville1 = new Ville();
        ville1.setId(1L);
        Ville ville2 = new Ville();
        ville2.setId(ville1.getId());
        assertThat(ville1).isEqualTo(ville2);
        ville2.setId(2L);
        assertThat(ville1).isNotEqualTo(ville2);
        ville1.setId(null);
        assertThat(ville1).isNotEqualTo(ville2);
    }

    @Test
    @Transactional
    public void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(VilleDTO.class);
        VilleDTO villeDTO1 = new VilleDTO();
        villeDTO1.setId(1L);
        VilleDTO villeDTO2 = new VilleDTO();
        assertThat(villeDTO1).isNotEqualTo(villeDTO2);
        villeDTO2.setId(villeDTO1.getId());
        assertThat(villeDTO1).isEqualTo(villeDTO2);
        villeDTO2.setId(2L);
        assertThat(villeDTO1).isNotEqualTo(villeDTO2);
        villeDTO1.setId(null);
        assertThat(villeDTO1).isNotEqualTo(villeDTO2);
    }

    @Test
    @Transactional
    public void testEntityFromId() {
        assertThat(villeMapper.fromId(42L).getId()).isEqualTo(42);
        assertThat(villeMapper.fromId(null)).isNull();
    }
}