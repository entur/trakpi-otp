package org.opentripplanner.trakpi.otp.kpi

import org.opentripplanner.trakpi.tester.spi.kpi.KPICalculator

/**
 * A [KPICalculator] for OTP requests that requires certain fields
 */
interface OtpKPICalculator : KPICalculator, OtpFieldRequirement
