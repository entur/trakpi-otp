package org.opentripplanner.trakpi.otp.kpi

import org.opentripplanner.trakpi.tester.spi.kpi.ComparativeResponseKPICalculator

/**
 * A [ComparativeResponseKPICalculator] for OTP requests that requires certain fields.
 */
interface OtpComparativeKPICalculator : ComparativeResponseKPICalculator, OtpFieldRequirement
