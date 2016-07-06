package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.analysis.service.IntervalActivityDTO.LevelOfDetail;

@JsonRootName("weekActivityOverview")
public class WeekActivityOverviewDTO extends IntervalActivityOverviewDTO
{
	private final Set<WeekActivityDTO> weekActivities;

	private WeekActivityOverviewDTO(ZonedDateTime date, Set<WeekActivityDTO> weekActivities)
	{
		super(date);
		this.weekActivities = weekActivities;
	}

	@Override
	protected String formatDateAsISO(LocalDate date)
	{
		return WeekActivityDTO.formatDate(date);
	}

	@JsonIgnore
	public Set<WeekActivityDTO> getWeekActivities()
	{
		return weekActivities;
	}

	static WeekActivityOverviewDTO createInstance(ZonedDateTime date, Set<WeekActivity> weekActivities)
	{
		return new WeekActivityOverviewDTO(date, weekActivities.stream()
				.map(a -> WeekActivityDTO.createInstance(a, LevelOfDetail.WeekOverview)).collect(Collectors.toSet()));
	}
}
