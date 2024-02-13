/*
 * Nextcloud Android client application
 *
 * @author Alper Ozturk
 * Copyright (C) 2024 Alper Ozturk
 * Copyright (C) 2024 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.extensions

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nextcloud.utils.extensions.getParcelableArgument
import com.nextcloud.utils.extensions.getSerializableArgument
import kotlinx.parcelize.Parcelize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Serializable

@RunWith(AndroidJUnit4::class)
class BundleExtensionTest {
    
    private val key = "testDataKey"

    @Test
    fun test_GetSerializableArgument_WhenGivenValidBundle_ShouldReturnExpectedData() {
        val bundle = Bundle()
        val testObject = TestData("Hello")
        bundle.putSerializable(key, testObject)
        val retrievedObject = bundle.getSerializableArgument(key, TestData::class.java)
        assertEquals(testObject, retrievedObject)
    }

    @Test
    fun test_GetSerializableArgument_WhenGivenValidBundleAndWrongClassType_ShouldReturnNull() {
        val bundle = Bundle()
        val testObject = TestData("Hello")
        bundle.putSerializable(key, testObject)
        val retrievedObject = bundle.getSerializableArgument(key, Array<String>::class.java)
        assertNull(retrievedObject)
    }

    @Test
    fun test_GetParcelableArgument_WhenGivenValidBundleAndWrongClassType_ShouldReturnNull() {
        val bundle = Bundle()
        val testObject = TestData("Hello")
        bundle.putSerializable(key, testObject)
        val retrievedObject = bundle.getParcelableArgument(key, OtherTestData::class.java)
        assertNull(retrievedObject)
    }

    @Test
    fun test_GetParcelableArgument_WhenGivenValidBundle_ShouldReturnExpectedData() {
        val bundle = Bundle()
        val testObject = TestDataParcelable("Hello")
        bundle.putParcelable(key, testObject)
        val retrievedObject = bundle.getParcelableArgument(key, TestDataParcelable::class.java)
        assertEquals(testObject, retrievedObject)
    }

    @Test
    fun test_GetSerializableArgument_WhenGivenNullBundle_ShouldReturnNull() {
        val retrievedObject = (null as Bundle?).getSerializableArgument(key, TestData::class.java)
        assertNull(retrievedObject)
    }

    @Test
    fun test_GetParcelableArgument_WhenGivenNullBundle_ShouldReturnNull() {
        val retrievedObject = (null as Bundle?).getParcelableArgument(key, TestDataParcelable::class.java)
        assertNull(retrievedObject)
    }

    @Parcelize
    class OtherTestData : Parcelable

    data class TestData(val message: String) : Serializable

    data class TestDataParcelable(val message: String) : Parcelable {
        constructor(parcel: Parcel) : this(parcel.readString() ?: "")

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(message)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<TestDataParcelable> {
            override fun createFromParcel(parcel: Parcel): TestDataParcelable {
                return TestDataParcelable(parcel)
            }

            override fun newArray(size: Int): Array<TestDataParcelable?> {
                return arrayOfNulls(size)
            }
        }
    }
}
