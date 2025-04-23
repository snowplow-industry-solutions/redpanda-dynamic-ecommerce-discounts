export const USER_ID = 'user1';
export const DELAY_SECONDS_TO_FIRST_PING = 10;
export const DELAY_SECONDS_BETWEEN_PINGS = 10;
export const durationInSeconds = (numberOfPings) => DELAY_SECONDS_TO_FIRST_PING + numberOfPings * DELAY_SECONDS_BETWEEN_PINGS;