/*
 * Copyright 2016 Microprofile.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.microprofile.showcase.schedule.persistence;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import io.microprofile.showcase.bootstrap.BootstrapData;
import io.microprofile.showcase.schedule.model.Schedule;
import io.microprofile.showcase.schedule.model.adapters.LocalDateAdapter;
import io.microprofile.showcase.schedule.model.adapters.LocalTimeAdapter;

@ApplicationScoped
public class ScheduleDAO {


    @Inject
    BootstrapData bootstrapData;
    
    @Inject
	MetricRegistry metrics;


    private final AtomicInteger sequence = new AtomicInteger(0);

    private final Map<String, Schedule> scheduleMap = new ConcurrentHashMap<>();
    private final Map<String, String> venues = new ConcurrentHashMap<>();

    @PostConstruct
    private void initStore() {
        Logger.getLogger(ScheduleDAO.class.getName()).log(Level.INFO, "Initialise schedule DAO from bootstrap data");

        final LocalDateAdapter dateAdapter = new LocalDateAdapter();
        final LocalTimeAdapter timeAdapter = new LocalTimeAdapter();
		Gauge<Long> gauge1 = metrics.getGauges().get("io.microprofile.showcase.schedule.persistence.ScheduleDAO.scheduleGauge");
		if (gauge1 == null) {
			gauge1 = () -> { return (long) scheduleMap.size(); }; 
			metrics.register("io.microprofile.showcase.schedule.persistence.ScheduleDAO.scheduleGauge", gauge1);
		}
		Gauge<Long> gauge2 = metrics.getGauges().get("io.microprofile.showcase.schedule.persistence.ScheduleDAO.venueGauge");
		if (gauge2 == null) {
			gauge2 = () -> { return (long) venues.size(); }; 
			metrics.register("io.microprofile.showcase.schedule.persistence.ScheduleDAO.venueGauge", gauge2);
		}
        

        bootstrapData.getSchedules()
                .forEach(bootstrap -> {

                    try {

                        String venueId = null;
                        for (final String key : venues.keySet()) {
                            final String v = venues.get(key);
                            if (v.equals(bootstrap.getVenue())) {
                                // existing venue
                                venueId = key;
                                break;
                            }
                        }

                        // generate a new key
                        if (null == venueId)
                            venueId = String.valueOf(sequence.incrementAndGet());

                        final Schedule sched = new Schedule(
                                bootstrap.getId(),
                                bootstrap.getSessionId(),
                                bootstrap.getVenue(),
                                venueId,
                                dateAdapter.unmarshal(bootstrap.getDate()),
                                timeAdapter.unmarshal(bootstrap.getStartTime()),
                                Duration.ofMinutes(new Double(bootstrap.getLength()).longValue())
                        );


                        scheduleMap.put(bootstrap.getId(), sched);
                        venues.put(venueId, sched.getVenue());

                    } catch (final Exception e) {
                        System.out.println("Failed to parse bootstrap data: " + e.getMessage());
                    }

                });

    }

    @Counted(monotonic = true)
    public Schedule addSchedule(final Schedule schedule) {

        final String id = String.valueOf(sequence.incrementAndGet());
        schedule.setId(id);

        if (schedule.getSessionId() == null) {
            schedule.setSessionId(String.valueOf(sequence.incrementAndGet()));
        }

        scheduleMap.put(id, schedule);

        return schedule;
    }
    
    @Timed
    public List<Schedule> getAllSchedules() {
        return new ArrayList<>(scheduleMap.values());
    }
    
    @Counted(monotonic = true)
    public Optional<Schedule> findById(final String id) {
        return Optional.ofNullable(scheduleMap.get(id));
    }
    
    @Counted(monotonic = true)
    public Schedule updateSchedule(final Schedule schedule) {
        if (schedule.getId() == null) {
            return addSchedule(schedule);
        }

        scheduleMap.put(schedule.getId(), schedule);
        return schedule;
    }
    @Counted(monotonic = true)
    public void deleteSchedule(final String scheduleId) {
        if (scheduleId != null) {
            scheduleMap.remove(scheduleId);
        }
    }

    @Counted(monotonic = true)
    public List<Schedule> findByVenue(final String venueId) {
        return scheduleMap.values()
                .stream()
                .filter(schedule -> schedule.getVenueId().equals(venueId))
                .collect(Collectors.toList());
    }

    @Counted(monotonic = true)
    public List<Schedule> findByDate(final LocalDate date) {
        return scheduleMap.values()
                .stream()
                .filter(schedule -> schedule.getDate().equals(date))
                .collect(Collectors.toList());
    }
}
