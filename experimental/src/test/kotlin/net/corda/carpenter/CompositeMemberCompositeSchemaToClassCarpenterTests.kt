package net.corda.carpenter

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.amqp.*
import org.junit.Test
import kotlin.test.assertEquals

class CompositeMemberCompositeSchemaToClassCarpenterTests {
    private var factory = SerializerFactory()

    fun serialise(clazz: Any) = SerializationOutput(factory).serialize(clazz)

    @Test
    fun nestedInts() {
        val testA = 10
        val testB = 20

        @CordaSerializable
        data class A(val a: Int)

        @CordaSerializable
        class B (val a: A, var b: Int)

        var b = B(A(testA), testB)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise(b))

        assert(obj.first is B)

        val amqpObj = obj.first as B

        assertEquals(testB, amqpObj.b)
        assertEquals(testA, amqpObj.a.a)
        assertEquals(2, obj.second.schema.types.size)
        assert(obj.second.schema.types[0] is CompositeType)
        assert(obj.second.schema.types[1] is CompositeType)

        println (obj.second.schema.types[0])
        println (obj.second.schema.types[1])


        var amqpSchemaA : CompositeType? = null
        var amqpSchemaB : CompositeType? = null

        for (type in obj.second.schema.types) {
            when (type.name.split ("$").last()) {
                "A" -> amqpSchemaA = type as CompositeType
                "B" -> amqpSchemaB = type as CompositeType
            }
        }

        assert (amqpSchemaA != null)
        assert (amqpSchemaB != null)

        assertEquals(1,     amqpSchemaA?.fields?.size)
        assertEquals("a",   amqpSchemaA!!.fields[0].name)
        assertEquals("int", amqpSchemaA!!.fields[0].type)

        assertEquals(2,     amqpSchemaB?.fields?.size)
        assertEquals("a",   amqpSchemaB!!.fields[0].name)
        assertEquals("net.corda.carpenter.CompositeMemberCompositeSchemaToClassCarpenterTests\$nestedInts\$A",
                amqpSchemaB!!.fields[0].type)
        assertEquals("b",   amqpSchemaB!!.fields[1].name)
        assertEquals("int", amqpSchemaB!!.fields[1].type)




//        assertEquals(2,     amqpSchema.fields.size)
//        assertEquals("a",   amqpSchema.fields[0].name)
//        assertEquals("int", amqpSchema.fields[0].type)
//        assertEquals("b",   amqpSchema.fields[1].name)
//        assertEquals("int", amqpSchema.fields[1].type)

        /*



        assertEquals(2,     amqpSchema.fields.size)
        assertEquals("a",   amqpSchema.fields[0].name)
        assertEquals("int", amqpSchema.fields[0].type)
        assertEquals("b",   amqpSchema.fields[1].name)
        assertEquals("int", amqpSchema.fields[1].type)

        var pinochio = ClassCarpenter().build(ClassCarpenter.Schema(amqpSchema.name, amqpSchema.carpenterSchema()))

        val p = pinochio.constructors[0].newInstance(testA, testB)

        assertEquals(pinochio.getMethod("getA").invoke(p), amqpObj.a)
        assertEquals(pinochio.getMethod("getB").invoke(p), amqpObj.b)
        */
    }

}

