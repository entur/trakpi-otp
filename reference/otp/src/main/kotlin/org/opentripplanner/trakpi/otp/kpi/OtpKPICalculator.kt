package org.opentripplanner.trakpi.otp.kpi

import org.opentripplanner.trakpi.tester.spi.kpi.ResponseKPICalculator

/**
 * A [ResponseKPICalculator] for OTP requests that requires certain fields
 */
interface OtpKPICalculator : ResponseKPICalculator, OtpFieldRequirement
