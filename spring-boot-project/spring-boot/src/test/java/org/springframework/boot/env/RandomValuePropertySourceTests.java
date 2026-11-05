/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.env;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.spy;

/**
 * Tests for {@link RandomValuePropertySource}.
 *
 * @author Dave Syer
 * @author Matt Benson
 */
public class RandomValuePropertySourceTests {

	private RandomValuePropertySource source = new RandomValuePropertySource();

	@Test
	public void notRandom() {
		assertThat(this.source.getProperty("foo")).isNull();
	}

	@Test
	public void string() {
		assertThat(this.source.getProperty("random.string")).isNotNull().asString().containsPattern("^[0-9a-f]{32}$");
	}

	@Test
	public void sourceIsSecureRandom() {
		assertThat(this.source.getSource()).isInstanceOf(SecureRandom.class);
	}

	@Test
	public void intValuesCannotBePredictedFromPreviousValues() {
		int first = (Integer) this.source.getProperty("random.int");
		int second = (Integer) this.source.getProperty("random.int");
		int third = (Integer) this.source.getProperty("random.int");
		Integer predicted = predictNextIntOfJavaUtilRandom(first, second);
		assertThat(predicted == null || predicted != third).isTrue();
	}

	/**
	 * Recover the internal state of a {@link Random} linear congruential generator from
	 * two consecutive {@code nextInt()} values and use it to predict the next one.
	 * @param first the first observed value
	 * @param second the second observed value
	 * @return the predicted next value or {@code null} if no matching state was found
	 */
	private Integer predictNextIntOfJavaUtilRandom(int first, int second) {
		long multiplier = 0x5DEECE66DL;
		long addend = 0xBL;
		long mask = (1L << 48) - 1;
		for (long low = 0; low < (1L << 16); low++) {
			long seed = ((first & 0xFFFFFFFFL) << 16) | low;
			long next = (seed * multiplier + addend) & mask;
			if ((int) (next >>> 16) == second) {
				long predicted = (next * multiplier + addend) & mask;
				return (int) (predicted >>> 16);
			}
		}
		return null;
	}

	@Test
	public void intValue() {
		Integer value = (Integer) this.source.getProperty("random.int");
		assertThat(value).isNotNull();
	}

	@Test
	public void uuidValue() {
		String value = (String) this.source.getProperty("random.uuid");
		assertThat(value).isNotNull();
		assertThat(UUID.fromString(value)).isNotNull();
	}

	@Test
	public void intRange() {
		Integer value = (Integer) this.source.getProperty("random.int[4,10]");
		assertThat(value).isNotNull();
		assertThat(value >= 4).isTrue();
		assertThat(value < 10).isTrue();
	}

	@Test
	public void intMax() {
		Integer value = (Integer) this.source.getProperty("random.int(10)");
		assertThat(value).isNotNull().isLessThan(10);
	}

	@Test
	public void longValue() {
		Long value = (Long) this.source.getProperty("random.long");
		assertThat(value).isNotNull();
	}

	@Test
	public void longRange() {
		Long value = (Long) this.source.getProperty("random.long[4,10]");
		assertThat(value).isNotNull().isBetween(4L, 10L);
	}

	@Test
	public void longMax() {
		Long value = (Long) this.source.getProperty("random.long(10)");
		assertThat(value).isNotNull().isLessThan(10L);
	}

	@Test
	public void longOverflow() {
		RandomValuePropertySource source = spy(this.source);
		given(source.getSource()).willReturn(new Random() {

			@Override
			public long nextLong() {
				// constant that used to become -8, now becomes 8
				return Long.MIN_VALUE;
			}

		});
		Long value = (Long) source.getProperty("random.long(10)");
		assertThat(value).isNotNull().isGreaterThanOrEqualTo(0L).isLessThan(10L);
		value = (Long) source.getProperty("random.long[4,10]");
		assertThat(value).isNotNull().isGreaterThanOrEqualTo(4L).isLessThan(10L);
	}

}
