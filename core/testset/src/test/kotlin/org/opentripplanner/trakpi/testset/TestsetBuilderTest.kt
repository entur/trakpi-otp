package org.opentripplanner.trakpi.testset

import kotlin.test.Test
import kotlin.test.assertEquals
import org.opentripplanner.trakpi.common.TestsetVersion

class TestsetBuilderTest {
    /** A trivial working request whose in-memory form is just its text. */
    private data class TextRequest(val id: String, val text: String)

    private val codec =
        object : RequestCodec<TextRequest> {
            override fun deserialize(request: Request) = TextRequest(request.id, request.body)
            override fun serialize(request: TextRequest) = Request(request.id, request.text)
        }

    private val noOpStore =
        object : TestsetStore {
            override fun store(testset: Testset) = Unit
            override fun versions(api: String) = emptyList<TestsetVersion>()
        }

    @Test
    fun `skips a request whose id repeats an earlier one and still fills the target size`() {
        val source = TestsetSource { listOf(Request("a", "1"), Request("a", "2"), Request("b", "3"), Request("c", "4")) }

        val result = TestsetBuilder(source, codec, emptyList(), noOpStore, targetSize = 3).prepare("transmodel", TestsetVersion("2026-07-16"))

        assertEquals(listOf(Request("a", "1"), Request("b", "3"), Request("c", "4")), result.requests)
    }

    @Test
    fun `applies every transform to every request in order, letting a transform self-target`() {
        val source = TestsetSource { listOf(Request("a", "trip"), Request("b", "nearest")) }
        val transforms =
            listOf<RequestTransform<TextRequest>>(
                RequestTransform { if (it.text == "trip") it.copy(text = it.text + "-A") else it },
                RequestTransform { it.copy(text = it.text + "-Z") },
            )
        val stored = mutableListOf<Testset>()
        val store =
            object : TestsetStore {
                override fun store(testset: Testset) { stored += testset }
                override fun versions(api: String) = emptyList<TestsetVersion>()
            }

        val result = TestsetBuilder(source, codec, transforms, store).prepare("transmodel", TestsetVersion("2026-07-16"))

        // both transforms run on both requests, in order; the first self-targets "trip" only
        assertEquals(listOf("trip-A-Z", "nearest-Z"), result.requests.map { it.body })
        assertEquals(result, stored.single())
    }
}
