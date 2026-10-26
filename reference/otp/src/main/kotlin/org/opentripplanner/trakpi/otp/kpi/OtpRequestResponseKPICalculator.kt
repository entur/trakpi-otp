package org.opentripplanner.trakpi.otp.kpi

import org.opentripplanner.trakpi.tester.spi.kpi.RequestResponseKPICalculator

/**
 * A [RequestResponseKPICalculator] for OTP requests that requires certain fields.
 */
interface OtpRequestResponseKPICalculator : RequestResponseKPICalculator, OtpFieldRequirement
