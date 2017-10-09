package eu.h2020.symbiote;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebAppConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = {CoreInterfaceApplication.class, SwaggerConfig.class},
                properties = {"eureka.client.enabled=false",
                              "spring.sleuth.enabled=false"})
@AutoConfigureMockMvc
public class Swagger2MarkupTest {

    private static final Logger LOG = LoggerFactory.getLogger(Swagger2MarkupTest.class);


    @Autowired
    private MockMvc mockMvc;

//    @Test
//    public void addANewPetToTheStore() throws Exception {
//        this.mockMvc.perform(post("/pets/").content(createPet())
//                .contentType(MediaType.APPLICATION_JSON))
//                .andDo(document("addPetUsingPOST", preprocessResponse(prettyPrint())))
//                .andExpect(status().isOk());
//    }

    @Test
    public void createSpringfoxSwaggerJson() throws Exception {
        //String designFirstSwaggerLocation = Swagger2MarkupTest.class.getResource("/swagger.yaml").getPath();

        String outputDir = System.getProperty("io.springfox.staticdocs.outputDir");
        MvcResult mvcResult = this.mockMvc.perform(get("/v2/api-docs")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpServletResponse response = mvcResult.getResponse();
        String swaggerJson = response.getContentAsString();
        Files.createDirectories(Paths.get(outputDir));
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputDir, "swagger.json"), StandardCharsets.UTF_8)){
            writer.write(swaggerJson);
        }
    }

//    private String createPet() throws JsonProcessingException {
//        Pet pet = new Pet();
//        pet.setId(1l);
//        pet.setName("Wuffy");
//        Category category = new Category(1l, "Hund");
//        pet.setCategory(category);
//        return new ObjectMapper().writeValueAsString(pet);
//    }
}


