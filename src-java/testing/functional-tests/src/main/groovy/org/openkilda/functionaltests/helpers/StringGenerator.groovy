package org.openkilda.functionaltests.helpers

import com.github.javafaker.Faker

import java.text.SimpleDateFormat

class StringGenerator {

    static def random = new Random()
    static def faker = new Faker()


    static String generateFlowId() {
        return new SimpleDateFormat("ddMMMHHmmss_SSS", Locale.US).format(new Date()) + "_" +
                faker.food().ingredient().toLowerCase().replaceAll(/\W/, "") + faker.number().digits(4)
    }

    static String generateDescription() {
        def methods = ["asYouLikeItQuote", "kingRichardIIIQuote", "romeoAndJulietQuote", "hamletQuote"]
        sprintf("autotest flow: %s", faker.shakespeare()."${methods[random.nextInt(methods.size())]}"())
    }
}
