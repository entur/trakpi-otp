package org.opentripplanner.trakpi.otp.kpi

import org.opentripplanner.trakpi.tester.spi.kpi.ComparativeKPICalculator

/**
 * A [ComparativeKPICalculator] for OTP requests that requires certain fields.
 */
interface OtpComparativeKPICalculator : ComparativeKPICalculator, OtpFieldRequirement
