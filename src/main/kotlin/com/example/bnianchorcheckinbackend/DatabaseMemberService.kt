package com.example.bnianchorcheckinbackend

import com.example.bnianchorcheckinbackend.entities.Member
import com.example.bnianchorcheckinbackend.entities.Guest
import com.example.bnianchorcheckinbackend.entities.MemberStanding
import com.example.bnianchorcheckinbackend.repositories.MemberRepository
import com.example.bnianchorcheckinbackend.repositories.GuestRepository
import com.example.bnianchorcheckinbackend.repositories.ProfessionGroupRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class DatabaseMemberService(
    private val memberRepository: MemberRepository,
    private val guestRepository: GuestRepository,
    private val professionGroupRepository: ProfessionGroupRepository
) {

    fun getAllMembers(): List<Map<String, Any>> {
        val groupByName = professionGroupRepository.findAll().associate { it.code to it.name }
        return memberRepository.findAllByOrderByNameAsc().map { member ->
            mapOf(
                "id" to (member.id!!.toInt()),
                "name" to member.name,
                "domain" to (member.profession ?: ""),
                "standing" to member.standing.name,
                "professionGroupName" to (groupByName[member.professionCode] ?: "")
            )
        }
    }

    fun getAllGuests(): List<Map<String, String>> {
        return guestRepository.findAllByOrderByNameAsc().map { guest ->
            mapOf(
                "name" to guest.name,
                "profession" to guest.profession,
                "referrer" to (guest.referrer ?: ""),
                "eventDate" to (guest.eventDate ?: "")
            )
        }
    }

    fun getMemberByName(name: String): MemberData? {
        val member = memberRepository.findByNameIgnoreCase(name).orElse(null) ?: return null
        return MemberData(
            name = member.name,
            domain = member.profession ?: "",
            type = "Member",
            membershipId = member.membershipId,
            referrer = null,
            standing = member.standing
        )
    }

    fun getGuestByName(name: String): GuestData? {
        val guest = guestRepository.findByNameIgnoreCase(name).orElse(null) ?: return null
        return GuestData(
            name = guest.name,
            profession = guest.profession,
            referrer = guest.referrer ?: ""
        )
    }

    @Transactional
    fun updateMemberStanding(name: String, standing: MemberStanding): Member? {
        val member = memberRepository.findByNameIgnoreCase(name).orElse(null) ?: return null
        member.standing = standing
        return memberRepository.save(member)
    }

    @Transactional
    fun updateMember(name: String, profession: String?, standing: MemberStanding?): Member? {
        val member = memberRepository.findByNameIgnoreCase(name).orElse(null) ?: return null
        if (profession != null) {
            member.profession = profession
        }
        if (standing != null) {
            member.standing = standing
        }
        return memberRepository.save(member)
    }

    @Transactional
    fun deleteMember(name: String): Boolean {
        val member = memberRepository.findByNameIgnoreCase(name).orElse(null) ?: return false
        memberRepository.delete(member)
        return true
    }

    @Transactional
    fun updateGuest(name: String, profession: String?, referrer: String?, eventDate: String?): Guest? {
        val guest = guestRepository.findByNameIgnoreCase(name).orElse(null) ?: return null
        profession?.let { guest.profession = it }
        referrer?.let { guest.referrer = it }
        eventDate?.let { guest.eventDate = it }
        return guestRepository.save(guest)
    }

    @Transactional
    fun deleteGuest(name: String): Boolean {
        val guest = guestRepository.findByNameIgnoreCase(name).orElse(null) ?: return false
        guestRepository.delete(guest)
        return true
    }
}
